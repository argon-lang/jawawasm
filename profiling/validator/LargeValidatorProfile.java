import java.nio.file.*;
import java.util.*;
import java.time.Duration;
import java.util.concurrent.ForkJoinPool;
import jdk.jfr.*;
import dev.argon.jawawasm.format.binary.ModuleReader;
import dev.argon.jawawasm.format.instructions.*;
import dev.argon.jawawasm.format.modules.ImportDesc;
import dev.argon.jawawasm.engine.validator.ModuleValidator;
public class LargeValidatorProfile {
 static int maxNesting;
 record Pending(List<? extends Instr> body, int depth) {}
 static long countInstructions(List<? extends Instr> body) {
  var pending = new ArrayDeque<Pending>(); pending.add(new Pending(body, 0)); long count=0;
  while(!pending.isEmpty()) {
   var item=pending.removeLast(); var instructions=item.body(); count+=instructions.size(); int depth=item.depth(); maxNesting=Math.max(maxNesting, depth);
   for(var instr:instructions) {
    switch(instr) {
     case ControlInstr.Block block -> pending.add(new Pending(block.body(), depth+1));
     case ControlInstr.Loop loop -> pending.add(new Pending(loop.body(), depth+1));
     case ControlInstr.If branch -> { pending.add(new Pending(branch.thenBody(), depth+1)); pending.add(new Pending(branch.elseBody(), depth+1)); }
     case ControlInstr.Try_Table table -> pending.add(new Pending(table.body(), depth+1));
     default -> {}
    }
   }
  }
  return count;
 }
 public static void main(String[] args) throws Exception {
  var file=Path.of(args[0]);
  dev.argon.jawawasm.format.modules.Module module;
  try(var input=Files.newInputStream(file)) { module=new ModuleReader(input).readModule(); }
  long[] sizes=module.funcs().stream().mapToLong(f -> countInstructions(f.body().body())).sorted().toArray();
  long imported=module.imports().stream().filter(i -> i.desc() instanceof ImportDesc.Func).count();
  long instructions=Arrays.stream(sizes).sum();
  System.out.printf("%s: bytes=%d definedFuncs=%d importedFuncs=%d instructions=%d avg=%.1f median=%d p95=%d max=%d maxNesting=%d cores=%d commonPool=%d%n",file.getFileName(),Files.size(file),sizes.length,imported,instructions,(double)instructions/sizes.length,sizes[sizes.length/2],sizes[(int)(sizes.length*.95)],sizes[sizes.length-1],maxNesting,Runtime.getRuntime().availableProcessors(),ForkJoinPool.getCommonPoolParallelism());
  for(int i=0;i<6;i++) ModuleValidator.validateModule(module);
  try(var r=new Recording(Configuration.getConfiguration("profile"))) {
   r.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1)); r.start();
   long start=System.nanoTime();int rounds=0;
   do { ModuleValidator.validateModule(module); rounds++; } while(System.nanoTime()-start<30_000_000_000L);
   long elapsed=System.nanoTime()-start;r.stop();r.dump(Path.of(args[1]));
   System.out.printf("Rounds: %d, ms/module: %.3f%n",rounds,elapsed/1e6/rounds);
  }
 }
}
