package sigmastate.verification.contract

import sigmastate.verification.SigmaDsl.api.sigma._
import stainless.lang._

import scala.language.postfixOps

sealed abstract class DummyContract extends SigmaContract {

  def contract(ctx: Context, limit: Int): SigmaProp = {
    import ctx._
    require(HEIGHT > 0 && limit > 0)
    // todo implicit boolops?
    sigmaProp(HEIGHT < limit)
  }
}

case object DummyContractVerification extends DummyContract {

  def proveTrue(ctx: Context, limit: Int): Boolean = {
    import ctx._
    require(HEIGHT < limit && HEIGHT > 0 && limit > 0)
    contract(ctx, limit).isValid
  } holds

  def proveFalse(ctx: Context, limit: Int): Boolean = {
    import ctx._
    require(HEIGHT > limit && HEIGHT > 0 && limit > 0)
    !contract(ctx, limit).isValid
  } holds

}