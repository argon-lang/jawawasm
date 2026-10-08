import java.nio.file.*;
import java.util.*;
import dev.argon.jawawasm.format.binary.ModuleReader;
import dev.argon.jawawasm.engine.validator.ModuleValidator;
import jdk.jfr.Recording;
import java.time.Duration;
public class ValidatorProfile {
 public static void main(String[] args) throws Exception {
  var modules = new ArrayList<dev.argon.jawawasm.format.modules.Module>();
  for(var f : Files.readAllLines(Path.of(args[0]))) {
   try(var in = Files.newInputStream(Path.of(f))) { modules.add(new ModuleReader(in).readModule()); }
  }
  for(var m : modules) ModuleValidator.validateModule(m);
  System.out.println("Modules: " + modules.size());
  for(int i=0;i<5;i++) for(var m:modules) ModuleValidator.validateModule(m);
  try(var r = new Recording(jdk.jfr.Configuration.getConfiguration("profile"))) {
   r.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
   r.start();
   long start=System.nanoTime(); int rounds=0;
   do { for(var m:modules) ModuleValidator.validateModule(m); rounds++; } while(System.nanoTime()-start<30_000_000_000L);
   long elapsed=System.nanoTime()-start;
   r.stop(); r.dump(Path.of(args[1]));
   System.out.printf("Rounds: %d, ms/corpus: %.3f%n",rounds,elapsed/1e6/rounds);
  }
 }
}
