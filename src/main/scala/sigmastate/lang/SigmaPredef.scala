package sigmastate.lang

import sigmastate.Values.SValue
import sigmastate._
import sigmastate.SCollection.SByteArray
import sigmastate.lang.Terms.{Lambda, Ident}

object SigmaPredef {

  case class PredefinedFunc(
    /** A name which is used in scripts */
    name: String,
    /** Function declaration without body */
    declaration: Lambda,
    /** Builder of SigmaIR node which is equivalent to function application
      * Rule: Apply(f, args) -->  irBuilder(f, args) */
    irBuilder: (SValue, Seq[SValue]) => SValue
  )

  /** Type variable used in the signatures of global functions below.*/
  private val tT = STypeIdent("T")

  val predefinedEnv: Map[String, SValue] = Seq(
    "allOf" -> Lambda(Vector("conditions" -> SCollection(SBoolean)), SBoolean, None),
    "anyOf" -> Lambda(Vector("conditions" -> SCollection(SBoolean)), SBoolean, None),
    "blake2b256" -> Lambda(Vector("input" -> SByteArray), SByteArray, None),
    "sha256" -> Lambda(Vector("input" -> SByteArray), SByteArray, None),
    "byteArrayToBigInt" -> Lambda(Vector("input" -> SByteArray), SBigInt, None),
    "intToByteArray" -> Lambda(Vector("input" -> SInt), SByteArray, None),
    "intToBigInt" -> Lambda(Vector("input" -> SInt), SBigInt, None),

    "getVar" -> Lambda(Vector("varId" -> SByte), tT, None),

    "proveDHTuple" -> Lambda(Vector(
      "g" -> SGroupElement, "h" -> SGroupElement, "u" -> SGroupElement, "v" -> SGroupElement), SBoolean, None),
    "proveDlog" -> Lambda(Vector("value" -> SGroupElement), SBoolean, None),
    "isMember" -> Lambda(Vector(
       "tree" -> SAvlTree, "key" -> SByteArray, "proof" -> SByteArray), SBoolean, None),
  ).toMap

  def PredefIdent(name: String) = {
    val v = predefinedEnv(name)
    Ident(name, v.tpe)
  }

  val AllSym = PredefIdent("allOf")
  val AnySym = PredefIdent("anyOf")

  val GetVarSym = PredefIdent("getVar")

  val Blake2b256Sym = PredefIdent("blake2b256")
  val Sha256Sym = PredefIdent("sha256")
  val IsMemberSym = PredefIdent("isMember")
  val ProveDlogSym = PredefIdent("proveDlog")
  val ProveDHTupleSym = PredefIdent("proveDHTuple")

  val IntToBigSym = PredefIdent("intToBigInt")
}
