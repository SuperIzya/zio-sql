package zio.sql

object Sql {
  type ColumnName = String
  type TableName
  type ColumnSchema[_]

  sealed trait TypeTag[A]
  object TypeTag {
    case object TInt extends TypeTag[Int]
    case object TLong extends TypeTag[Long]
    case object TBoolean extends TypeTag[Boolean]
    case object TString extends TypeTag[String]
  }

  sealed trait PrimType[A] {
    def typeTag: TypeTag[A]
  }
  object PrimType {
    implicit val SqlTypeBoolean: PrimType[Boolean] = new PrimType[Boolean] {
      val typeTag = TypeTag.TBoolean
    }
    implicit val SqlTypeInt: PrimType[Int] = new PrimType[Int] {
      val typeTag = TypeTag.TInt 
    }
    implicit val SqlTypeLong: PrimType[Long] = new PrimType[Long] {
      val typeTag = TypeTag.TLong
    }
    implicit val SqlTypeString: PrimType[String] = new PrimType[String] {
      val typeTag = TypeTag.TString
    }
  }

  final case class FunctionName(name: String)

  final case class Column[A: PrimType](name: String) {
    def typeTag: TypeTag[A] = implicitly[PrimType[A]].typeTag
  }
  object Column {
    def int(name: String): Column[Int] = Column[Int](name)
    def long(name: String): Column[Long] = Column[Long](name)
    def string(name: String): Column[String] = Column[String](name)
  }

  sealed trait Table[A] { self =>
    def apply[B](f: Table[A] => Column[B]): Expr[A, B] = Expr.Source(self, f(self))
  }
  object Table {
    final case class Source[A](name: TableName, columnSchema: ColumnSchema[A]) extends Table[A]
  }

  /**
   * (SELECT *, "foo", table.a + table.b AS sum... FROM table WHERE cond) UNION (SELECT ... FROM table)
   *   UNION ('1', '2', '3')
   * UPDATE table SET ...
   * INSERT ... INTO table
   * DELETE ... FROM table
   */
  sealed trait Sql[-A, +B]

  /**
   * A `Read[A]` models a selection of a set of values of type `A`.
   */
  sealed trait Read[+A]
  object Read {
    final case class Select[A, B](
      selection: Selection[A, B], table: Table[A], where: Expr[A, Boolean]) extends Read[B]
    
    final case class Union[B](left: Read[B], right: Read[B], distinct: Boolean) extends Read[B]

    final case class Literal[B: PrimType](values: Iterable[B]) extends Read[B]
  }

  /**
   * A columnar selection of `B` from a source `A`, modeled as `A => B`.
   */
  sealed trait Selection[-A, +B]
  object Selection {
    final case class Identity[A]() extends Selection[A, A]
    final case class Constant[A: PrimType](value: A) extends Selection[Any, A]
    final case class Concat[A, L, R](left: Selection[A, L], right: Selection[A, R]) 
      extends Selection[A, (L, R)]
    final case class Computed[A, B](expr: Expr[A, B], name: Option[ColumnName]) extends 
      Selection[A, B]
  }

  sealed trait BinaryOp[A]
  object BinaryOp {
    case object AddInt extends BinaryOp[Int]
    case object SubInt extends BinaryOp[Int]
    case object MultInt extends BinaryOp[Int]
    case object DivInt extends BinaryOp[Int]
    case object ModInt extends BinaryOp[Int]
    case object AddLong extends BinaryOp[Long]
    case object SubLong extends BinaryOp[Long]
    case object MultLong extends BinaryOp[Long]
    case object DivLong extends BinaryOp[Long]
    case object ModLong extends BinaryOp[Long]
    case object AndBool extends BinaryOp[Boolean]
    case object OrBool extends BinaryOp[Boolean]
    case object StringConcat extends BinaryOp[String]
  }

  /**
   * Models a function `A => B`.
   * SELECT product.price + 10
   */
  sealed trait Expr[-A, +B]
  object Expr {
    final case class Source[A, B](table: Table[A], column: Column[B]) extends Expr[A, B]
    final case class Binary[A, B](left: Expr[A, B], right: Expr[A, B], op: BinaryOp[B]) extends Expr[A, B]
    final case class Literal[B: PrimType](value: B) extends Expr[Any, B]
    final case class FunctionCall[A, B, C](value: Expr[A, B], function: FunctionDef[B, C])
      extends Expr[A, C]
  }

  final case class FunctionDef[-A, +B](name: FunctionName)
}

/*
select(age * 2 ~ name ~ username)
  .from(person)
  .where(age === lit(42))
*/

/*
sealed trait Predicate[-A]
  // sealed trait ExistencePredicate extends Predicate[Any]
  // object Predicate {
  //   final case class Equal[A](right: A) extends Predicate[A]
  //   final case class NoEqual[A](right: A) extends Predicate[A]
  //   final case class GreaterThan[A](right: A) extends Predicate[A]
  //   final case class LessThan[A](right: A) extends Predicate[A]
  //   final case class GreaterThanOrEqual[A](right: A) extends Predicate[A]
  //   final case class LessThanOrEqual[A](right: A) extends Predicate[A]
  //   case object IsNull extends Predicate[Any]
  //   case object IsNotNull extends Predicate[Any]
  //   case object IsTrue extends Predicate[Boolean]
  //   case object IsNotTrue extends Predicate[Boolean]
  //   case object IsFalse extends Predicate[Boolean]
  //   case object IsNotFalse extends Predicate[Boolean]
  //   final case class Between[A](lower: A, upper: A) extends Predicate[A]
  //   final case class Like(right: String) extends Predicate[String]
    
  //   final case class In[A](right: Set[A]) extends Predicate[A] //could be applied to a subquery result
  //   final case class NotIn[A](right: Set[A]) extends Predicate[A] //could be applied to a subquery result
    
  //   final case class Exists(read: Read[_]) extends ExistencePredicate
  //   final case class NotExists(read: Read[_]) extends ExistencePredicate
    
  //   //TODO Any, All
  // }
*/