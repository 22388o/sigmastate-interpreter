package special.sigma

import scala.reflect.ClassTag
import special.collection.Coll
import scalan.{NeverInline, RType, Reified}

class TestBox(
  val id: Coll[Byte],
  val value: Long,
  val bytes: Coll[Byte],
  val bytesWithoutRef: Coll[Byte],
  val propositionBytes: Coll[Byte],
  val registers: Coll[AnyValue]) extends Box
{
  def builder = new TestSigmaDslBuilder
  @NeverInline
  def getReg[T](id: Int)(implicit cT: RType[T]): Option[T] = {
    implicit val tag: ClassTag[T] = cT.classTag
    if (id < 0 || id >= registers.length) return None
    val value = registers(id)
    if (value != null ) {
      // once the value is not null it should be of the right type
      value match {
        case value: TestValue[_] if value.value != null =>
          Some(value.value.asInstanceOf[T])
        case _ =>
          throw new InvalidType(s"Cannot getVar($id): invalid type of value $value at id=$id")
      }
    } else None
  }
  @NeverInline
  def cost = (dataSize / builder.CostModel.AccessKiloByteOfData.toLong).toInt
  @NeverInline
  def dataSize = bytes.length

  def creationInfo: (Int, Coll[Byte]) = this.getReg[(Int, Coll[Byte])](3).get

  def tokens: Coll[(Coll[Byte], Long)] = {
    this.getReg[Coll[(Coll[Byte], Long)]](2).get
  }

  @NeverInline
  override def executeFromRegister[@Reified T](regId: Byte)(implicit cT: RType[T]): T = ???
}
