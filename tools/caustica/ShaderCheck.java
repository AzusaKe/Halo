package dev.comfyfluffy.caustica.slang;
import java.nio.file.*;
import java.util.*;
public final class ShaderCheck {
 public static void main(String[] args) throws Exception {
  var library=SlangLibrary.load(Path.of(args[0]),SlangPlatform.current());
  var runtime=library.createRuntime();
  try {
   var session=library.createSession(runtime,List.of(Path.of(args[1]),Path.of(args[2])),SlangLibrary.SESSION_WARNINGS_AS_ERRORS);
   try {
    var source=Path.of(args[3]);
    var result=library.compile(session,"caustica_shader_check",source.toString(),Files.readString(source),"main");
    Files.write(Path.of(args[4]),result.spirv());
    Files.writeString(Path.of(args[4]+".json"),result.reflectionJson());
    System.out.println("Slang "+library.compilerVersion()+": "+result.spirv().length+" bytes; "+result.diagnostics());
   } finally {library.destroySession(session);}
  } finally {library.destroyRuntime(runtime);}
 }
}
