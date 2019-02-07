package sigmastate.serialization

import sigmastate.Values.{BoolValue, SigmaPropValue}
import sigmastate.lang.Terms._
import sigmastate.serialization.OpCodes._
import sigmastate.utils.{SigmaByteReader, SigmaByteWriter}
import sigmastate.{BoolToSigmaProp, SType, Values}

case class BoolToSigmaPropSerializer(cons: BoolValue => SigmaPropValue) extends ValueSerializer[BoolToSigmaProp] {

  override val opCode: Byte = BoolToSigmaPropCode

  def serializeBody(obj: BoolToSigmaProp, w: SigmaByteWriter): Unit = {
    w.putValue(obj.value)
  }

  def parseBody(r: SigmaByteReader): Values.Value[SType] = {
    val p = r.getValue().asBoolValue
    cons(p)
  }
}