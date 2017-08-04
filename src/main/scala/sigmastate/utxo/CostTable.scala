package sigmastate.utxo


object CostTable {
  val ScriptLimit = 1000000

  object Cost {
    //Node which holds true/false
    val ConstantNode = 1

    val HeightAccess = 1

    //cost fo CustomByteArray declaration. Additional cost to be calculated when data is known
    //(and CustomByteArray being converted to ByteArrayLeaf)
    val ByteArrayDeclaration = 1

    val ByteArrayPerKilobyte = 100

    val TripleDeclaration = 1


    val AndDeclaration = 1
    val AndPerChild = 1


    val OrDeclaration = 1
    val OrPerChild = 1

    //PropLeaf declaration cost, wrapped script cost to be added as well.
    val PropLeafDeclaration = 500

    //Cost of Blake256 body (ByteArrayLeaf is only possible atm) is multiplied by factor
    val Blake256bFactor = 10

    val DlogCost = 10000
  }
}