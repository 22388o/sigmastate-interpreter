package sigmastate.eval

import java.math.BigInteger

import org.bouncycastle.math.ec.ECPoint
import org.ergoplatform.{ErgoLikeContext, ErgoLikeTransaction, ErgoBox}
import scapi.sigma.DLogProtocol
import sigmastate.SCollection.SByteArray
import sigmastate._
import sigmastate.Values.{LongConstant, FalseLeaf, TrueLeaf, BigIntConstant, SigmaPropConstant, ByteArrayConstant, IntConstant, BigIntArrayConstant, SigmaBoolean, ValUse}
import sigmastate.helpers.ErgoLikeProvingInterpreter
import sigmastate.interpreter.ContextExtension
import sigmastate.lang.DefaultSigmaBuilder.mkTaggedVariable
import sigmastate.lang.LangTests
import special.collection.{Col => VCol}
import special.sigma.{TestValue => VTestValue}

import scalan.BaseCtxTests

class CompilerItTest extends BaseCtxTests
    with LangTests with ExampleContracts with ErgoScriptTestkit {
  import IR._
  import builder._
  import WArray._
  import WOption._
  import ColBuilder._
  import Context._
  import Col._
  import SigmaProp._
  import CostedCol._
  import WBigInteger._
  import WECPoint._
  import ProveDlogEvidence._
  import ProveDHTEvidence._
  import sigmastate.serialization.OpCodes._
  import Liftables._
  import SType.AnyOps

  lazy val dsl = sigmaDslBuilder
  lazy val bigSym = liftConst(big)
  lazy val n1Sym = liftConst(n1)

  val backerProver = new ErgoLikeProvingInterpreter
  val projectProver = new ErgoLikeProvingInterpreter
  val backerPubKey = backerProver.dlogSecrets.head.publicImage
  val projectPubKey = projectProver.dlogSecrets.head.publicImage
  val ctxVars = contextVars(Map(
    backerPubKeyId -> backerPubKey,
    projectPubKeyId -> projectPubKey,
    3.toByte -> bigIntArr1
  )).arr

  val boxToSpend = ErgoBox(10, TrueLeaf,
    additionalRegisters = Map(ErgoBox.R4 -> BigIntArrayConstant(bigIntArr1)))
  val tx1Output1 = ErgoBox(minToRaise, projectPubKey)
  val tx1Output2 = ErgoBox(1, projectPubKey)
  val tx1 = ErgoLikeTransaction(IndexedSeq(), IndexedSeq(tx1Output1, tx1Output2))
  val ergoCtx = ErgoLikeContext(
    currentHeight = timeout - 1,
    lastBlockUtxoRoot = AvlTreeData.dummy,
    boxesToSpend = IndexedSeq(),
    spendingTransaction = tx1,
    self = boxToSpend,
    extension = ContextExtension(Map(
      backerPubKeyId -> SigmaPropConstant(backerPubKey),
      projectPubKeyId -> SigmaPropConstant(projectPubKey),
      3.toByte -> BigIntArrayConstant(bigIntArr1)
    )))

  def intConstCase = {
    Case[Int](env, "intConst", "1", ergoCtx,
      calc = {_ => 1 },
      cost = {_ => constCost[Int]},
      size = {_ => sizeOf(1)},
      tree = IntConstant(1), Result(1, 1, 4))
  }

  def bigIntegerConstCase = {
    Case(env, "bigIntegerConst", "big", ergoCtx,
      calc = {_ => bigSym },
      cost = {_ => constCost[WBigInteger]},
      size = {_ => sizeOf(bigSym)},
      tree = BigIntConstant(big), Result(big, 1, 16))
  }

  def addBigIntegerConstsCase = {
    val size = (sizeOf(bigSym) max sizeOf(n1Sym)) + 1L
    val res = big.add(n1)
    Case(env, "addBigIntegerConsts", "big + n1", ergoCtx,
      calc = {_ => bigSym.add(n1Sym) },
      cost = {_ => constCost[WBigInteger] + constCost[WBigInteger] +
          costOf("+", SFunc(Vector(SBigInt, SBigInt), SBigInt)) +
          costOf("+_per_item", SFunc(Vector(SBigInt, SBigInt), SBigInt)) * size.toInt },
      size = {_ => size },
      tree = mkPlus(BigIntConstant(big), BigIntConstant(n1)),
      Result(res, 119, 17))
  }

  def arrayConstCase = {
    val arr1 = env("arr1").asInstanceOf[Array[Byte]]
    val arr1Sym = liftConst(arr1)
    val col1Sym = colBuilder.fromArray[Byte](arr1Sym)
    val res = Cols.fromArray(arr1).arr
    Case(env, "arrayConst", "arr1", ergoCtx,
      calc = {_ => col1Sym },
      cost = {_ => constCost[Col[Byte]] },
      size = {_ => sizeOf(col1Sym) },
      tree = ByteArrayConstant(arr1), Result(res, 1, 2))
  }

  def bigIntArray_Map_Case = {
    import SCollection._
    val res = Cols.fromArray(bigIntArr1).map(n => n.add(n1)).arr
    val arrSym = colBuilder.fromArray(liftConst(bigIntArr1))
    Case(env, "bigIntArray_Map",
      "bigIntArr1.map(fun (i: BigInt) = i + n1)", ergoCtx,
      calc = { ctx =>
        val arr = liftConst(bigIntArr1)
        val vals = colBuilder.fromArray(arr)
        val costs = colBuilder.replicate(arr.length, constCost[WBigInteger])
        val sizes = colBuilder.fromArray(liftConst(bigIntArr1.map(x => SBigInt.dataSize(x.asWrappedType))))
        val arrC = RCostedCol(vals, costs, sizes, constCost[Col[WBigInteger]])
        vals.map(fun(n => n.add(liftConst(n1))))
      },
      cost = {_ =>
        val arr = liftConst(bigIntArr1)
        val opType = SFunc(Vector(SBigInt,SBigInt), SBigInt)
        val f = fun { in: Rep[(Int, Long)] =>
          val Pair(c, s) = in
          val c1 = c + constCost[WBigInteger] + costOf("+", opType)
          val c2 = costOf("+_per_item", opType) * ((s max sizeOf(liftConst(n1))) + 1L).toInt
          c1 + c2
        }
        val arrSizes = colBuilder.fromArray(liftConst(Array(1L, 1L)))
        val costs = colBuilder.replicate(arr.length, 0).zip(arrSizes).map(f)
        constCost[Col[WBigInteger]] + costs.sum(intPlusMonoid)
      },
      size = {_ =>
        val f = fun {s: Rep[Long] => (s max sizeOf(liftConst(n1))) + 1L}
        val arrSizes = colBuilder.fromArray(liftConst(Array(1L, 1L)))
        arrSizes.map(f).sum(longPlusMonoid)
      },
      tree = mkMapCollection1(
        BigIntArrayConstant(bigIntArr1),
        mkFuncValue(Vector((1,SBigInt)), ArithOp(ValUse(1,SBigInt), BigIntConstant(10L), -102))
      ),
      Result(res, 207, 4))
  }

  def bigIntArray_Where_Case = {
    import SCollection._
    Case(env, "bigIntArray_Where_Case",
      "bigIntArr1.where(fun (i: BigInt) = i > 0)", ergoCtx,
      calc = null,
      cost = null,
      size = null,
      tree = null,
      Result.Ignore)
  }

  def bigIntArray_Slice_Case = {
    import SCollection._
    Case(env, "bigIntArray_Slice_Case",
      "bigIntArr1.slice(0,1)", ergoCtx,
      calc = null,
      cost = null,
      size = null,
      tree = null,
      Result.Ignore)
  }

  def sigmaPropConstCase = {
    val resSym = RProveDlogEvidence(liftConst(g1.asInstanceOf[ECPoint]))
    val res = DLogProtocol.ProveDlog(g1) // NOTE! this value cannot be produced by test script
    Case(env, "sigmaPropConst", "p1", ergoCtx,
      calc = {_ => resSym },
      cost = {_ => constCost[WECPoint] + constCost[SigmaProp] },
      size = {_ => sizeOf(resSym) },
      tree = SigmaPropConstant(p1), Result(res, 1 + 1, 32 + 1))
  }

  def andSigmaPropConstsCase = {
    val p1Sym: Rep[SigmaProp] = RProveDlogEvidence(liftConst(g1.asInstanceOf[ECPoint]))
    val p2Sym: Rep[SigmaProp] = RProveDlogEvidence(liftConst(g2.asInstanceOf[ECPoint]))
    val resSym = (p1Sym && p2Sym).isValid
    Case(env, "andSigmaPropConsts", "p1 && p2", ergoCtx,
      calc = {_ => resSym },
      cost = {_ =>
        val c1 = constCost[WECPoint] + constCost[SigmaProp] +
                  costOf("SigmaPropIsValid", SFunc(SSigmaProp, SBoolean))
        c1 + c1 + costOf("BinAnd", SFunc(Vector(SBoolean, SBoolean), SBoolean))
      },
      size = {_ => sizeOf(resSym) },
      tree = SigmaAnd(Seq(SigmaPropConstant(p1), SigmaPropConstant(p2))).isValid,
      Result(AND(p1, p2), (1 + 1 + 1) * 2 + 1, 1))
  }

  def register_BinIntArr_Case = {
    import SCollection._
    Case(env, "register_BinIntArr_Case",
      "SELF.R4[Array[BigInt]].value", ergoCtx,
      calc = null,
      cost = null,
      size = null,
      tree = null,
      Result(bigIntArr1))
  }

  def register_BinIntArr_Map_Case = {
    import SCollection._
    Case(env, "contextVar_BinIntArr_Map_Case",
      "SELF.R4[Array[BigInt]].value.map(fun (i: BigInt) = i + n1)", ergoCtx,
      calc = null,
      cost = null,
      size = null,
      tree = null,
      Result.Ignore)
  }

  def register_BinIntArr_Where_Case = {
    import SCollection._
    Case(env, "contextVar_BinIntArr_Map_Case",
      "SELF.R4[Array[BigInt]].value.where(fun (i: BigInt) = i > 0)", ergoCtx,
      calc = null,
      cost = null,
      size = null,
      tree = null,
      Result.Ignore)
  }


  lazy val testCases = Seq[EsTestCase[_]](
    intConstCase , bigIntegerConstCase, addBigIntegerConstsCase, arrayConstCase, sigmaPropConstCase
  )

//  test("run all") {
//    for (c <- testCases)
//      c.doReduce()
//  }

  test("intConstCase") {
    intConstCase.doReduce
  }

  test("bigIntegerConstCase") {
    bigIntegerConstCase.doReduce
  }

  test("addBigIntegerConstsCase") {
    addBigIntegerConstsCase.doReduce()
  }

  test("arrayConstCase") {
    arrayConstCase.doReduce()
  }

  test("sigmaPropConstCase") {
    sigmaPropConstCase.doReduce()
  }

  test("andSigmaPropConstsCase") {
    andSigmaPropConstsCase.doReduce()
  }

  test("bigIntArray_Map_Case") {
    bigIntArray_Map_Case.doReduce()
  }

  test("bigIntArray_Slice_Case") {
    bigIntArray_Slice_Case.doReduce()
  }

  test("bigIntArray_Where_Case") {
    bigIntArray_Where_Case.doReduce()
  }

  test("register_BinIntArr_Case") {
    register_BinIntArr_Case.doReduce()
  }

//  test("register_BinIntArr_Map_Case") {
//    register_BinIntArr_Map_Case.doReduce()
//  }

}
