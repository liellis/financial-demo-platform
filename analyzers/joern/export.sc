import io.shiftleft.semanticcpg.language.*
import io.shiftleft.codepropertygraph.generated.nodes.*
import java.nio.file.{Files, Paths}

@main def exec(input: String, output: String) = {
  importCpg(input)
  def line(n: AstNode): Int = n.lineNumber.getOrElse(0)
  def end(n: AstNode): Int = n match { case m: Method => m.lineNumberEnd.getOrElse(line(n)); case t: TypeDecl => t.ast.lineNumber.l.maxOption.getOrElse(line(n)); case _ => line(n) }
  val types = cpg.typeDecl.filterNot(_.isExternal).filter(t => t.lineNumber.isDefined).l
  val methods = cpg.method.filterNot(_.isExternal).filter(m => m.lineNumber.isDefined).l
  val controls = cpg.controlStructure.l
  val entities = types.map(t => ujson.Obj("key" -> t.id.toString, "range_kind" -> "AST_COVERED_LINES", "kind" -> (if (t.code.matches("(?s).*\\binterface\\b.*")) "Interface" else "Class"), "name" -> t.name, "signature" -> t.fullName, "path" -> t.filename, "start_line" -> line(t), "end_line" -> end(t))) ++
    methods.map(m => ujson.Obj("key" -> m.id.toString, "parent" -> m.astParent.id.toString, "kind" -> "Method", "name" -> m.name, "signature" -> m.fullName, "path" -> m.filename, "start_line" -> line(m), "end_line" -> end(m))) ++
    controls.map(c => ujson.Obj("key" -> c.id.toString, "parent" -> c.method.id.toString, "kind" -> "ControlStructure", "name" -> c.controlStructureType, "signature" -> c.code, "path" -> c.method.filename, "start_line" -> line(c), "end_line" -> end(c)))
  val calls = cpg.call.filter(c => c.lineNumber.isDefined).map(c => ujson.Obj("from" -> c.method.id.toString, "name" -> c.name, "code" -> c.code, "path" -> c.method.filename, "start_line" -> line(c), "targets" -> ujson.Arr.from(c.callee.filterNot(_.isExternal).id.l.map(_.toString)), "external_targets" -> ujson.Arr.from(c.callee.filter(_.isExternal).fullName.l))).l
  val result = ujson.Obj("entities" -> ujson.Arr.from(entities), "calls" -> ujson.Arr.from(calls), "statistics" -> ujson.Obj("types" -> types.size,"methods" -> methods.size,"control_structures" -> controls.size,"calls" -> calls.size))
  Files.writeString(Paths.get(output), ujson.write(result))
}
