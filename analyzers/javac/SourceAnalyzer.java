import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.lang.model.element.Element;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

/** Read-only JDK AST analysis. Annotation processing and code generation are disabled. */
public class SourceAnalyzer {
    static Path root;
    static Trees trees;
    static List<Object> entities = new ArrayList<>(), calls = new ArrayList<>();
    static String path(CompilationUnitTree u) { return root.relativize(Path.of(u.getSourceFile().toUri())).toString().replace('\\','/'); }
    static long start(CompilationUnitTree u, Tree t) { return trees.getSourcePositions().getStartPosition(u,t); }
    static String key(CompilationUnitTree u, Tree t) { return path(u) + ":" + start(u,t); }
    static Map<String,Object> node(CompilationUnitTree u, Tree t, String kind, String name, String signature, String parent) {
        long a=start(u,t), b=trees.getSourcePositions().getEndPosition(u,t);
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("key",key(u,t)); m.put("kind",kind); m.put("name",name); m.put("signature",signature);
        m.put("path",path(u)); m.put("start_line",u.getLineMap().getLineNumber(Math.max(0,a)));
        m.put("end_line",u.getLineMap().getLineNumber(Math.max(a,b-1))); m.put("parent",parent);
        return m;
    }
    static class Scanner extends TreePathScanner<Void,Void> {
        CompilationUnitTree unit; String container="", parent=null, method=null;
        Scanner(CompilationUnitTree u) { unit=u; }
        public Void visitClass(ClassTree t,Void p) {
            String old=container, op=parent;
            container=(container.isEmpty()?"":container+".")+t.getSimpleName();
            String pkg=unit.getPackageName()==null?"":unit.getPackageName()+".";
            entities.add(node(unit,t,t.getKind()==Tree.Kind.INTERFACE?"Interface":"Class",t.getSimpleName().toString(),pkg+container,parent));
            parent=key(unit,t); super.visitClass(t,p); parent=op; container=old; return null;
        }
        public Void visitMethod(MethodTree t,Void p) {
            if(trees.getSourcePositions().getEndPosition(unit,t)<0)return null;
            String old=method, op=parent;
            String sig=container+"#"+t.getName()+"("+String.join(",",t.getParameters().stream().map(x->x.getType().toString()).toList())+")";
            entities.add(node(unit,t,"Method",t.getName().toString(),sig,parent));
            method=key(unit,t); parent=method; super.visitMethod(t,p); method=old; parent=op; return null;
        }
        public Void visitMethodInvocation(MethodInvocationTree t,Void p) {
            if(method!=null) {
                Map<String,Object> m=new LinkedHashMap<>(); m.put("from",method); m.put("path",path(unit));
                m.put("start_line",unit.getLineMap().getLineNumber(start(unit,t)));
                m.put("expression",t.getMethodSelect().toString()); m.put("target_key",null);
                try {
                    Element element=trees.getElement(getCurrentPath());
                    TreePath target=element==null?null:trees.getPath(element);
                    if(target!=null && target.getLeaf() instanceof MethodTree && start(target.getCompilationUnit(),target.getLeaf())>=0)
                        m.put("target_key",key(target.getCompilationUnit(),target.getLeaf()));
                } catch(Exception ignored) { }
                m.put("resolution_status",m.get("target_key")==null?"UNRESOLVED":"STATIC_TARGET");
                calls.add(m);
            }
            return super.visitMethodInvocation(t,p);
        }
    }
    static String json(Object o) {
        if(o==null)return "null";
        if(o instanceof Number || o instanceof Boolean)return o.toString();
        if(o instanceof Map<?,?> m) return "{"+String.join(",",m.entrySet().stream().map(e->json(e.getKey().toString())+":"+json(e.getValue())).toList())+"}";
        if(o instanceof List<?> l)return "["+String.join(",",l.stream().map(SourceAnalyzer::json).toList())+"]";
        StringBuilder b=new StringBuilder("\"");
        for(char c:o.toString().toCharArray()) switch(c) {
            case '"':b.append("\\\"");break;case '\\':b.append("\\\\");break;
            case '\n':b.append("\\n");break;case '\r':b.append("\\r");break;case '\t':b.append("\\t");break;
            default: if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);
        }
        return b.append('"').toString();
    }
    public static void main(String[] args) throws Exception {
        root=Path.of(args[0]).toRealPath();
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        if(compiler==null)throw new IllegalStateException("JDK_REQUIRED");
        DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        try(StandardJavaFileManager fm=compiler.getStandardFileManager(diagnostics,null,java.nio.charset.StandardCharsets.UTF_8)) {
            List<Path> paths=Arrays.stream(args).skip(1).map(root::resolve).toList();
            JavacTask task=(JavacTask)compiler.getTask(null,fm,diagnostics,List.of("-proc:none","-implicit:none","-encoding","UTF-8"),null,fm.getJavaFileObjectsFromPaths(paths));
            List<CompilationUnitTree> units=new ArrayList<>(); task.parse().forEach(units::add);
            trees=Trees.instance(task);
            try { task.analyze(); } catch(Exception ignored) { }
            for(CompilationUnitTree u:units)new Scanner(u).scan(u,null);
            List<Object> errors=new ArrayList<>();
            for(var d:diagnostics.getDiagnostics()) {
                if(errors.size()>=50)break;
                Map<String,Object> e=new LinkedHashMap<>();
                e.put("code",d.getCode());e.put("kind",d.getKind().toString());e.put("line",d.getLineNumber());
                if(d.getSource()!=null)e.put("path",root.relativize(Path.of(d.getSource().toUri())).toString());
                errors.add(e);
            }
            System.out.println(json(Map.of("entities",entities,"calls",calls,"diagnostics",errors,"analyzer","javac-ast-1","jdk",Runtime.version().toString(),"limitations",List.of("Static source targets only; virtual dispatch is not a proven runtime target.","Missing dependencies / Lombok / reflection / proxy targets may remain unresolved.","No build scripts, annotation processors or project code are executed."))));
        }
    }
}
