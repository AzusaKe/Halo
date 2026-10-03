import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.util.*;
public class JfrSummary {
 static double quantile(List<Double> data,double q){if(data.isEmpty())return Double.NaN;data.sort(Double::compare);return data.get((int)Math.round((data.size()-1)*q));}
 static String series(List<Double> d){return "{\"samples\":"+d.size()+",\"p50\":"+quantile(d,.5)+",\"p95\":"+quantile(d,.95)+",\"p99\":"+quantile(d,.99)+"}";}
 public static void main(String[] args)throws Exception{
  var loops=new ArrayList<Double>();var cpu=new ArrayList<Double>();var allocation=new ArrayList<Double>();var update=new ArrayList<Double>();var gpu=new HashMap<Long,long[]>();
  long[] first=null,last=null;long minInstances=Long.MAX_VALUE,maxInstances=0;var stages=new TreeMap<String,ArrayList<Double>>();
  try(var recording=new RecordingFile(Path.of(args[0]))){while(recording.hasMoreEvents()){
   var e=recording.readEvent();var type=e.getEventType().getName();
   if(type.equals("dev.comfyfluffy.caustica.HostLoop") && e.getBoolean("rtActive"))loops.add(e.getLong("elapsedNanos")/1e6);
   if(type.equals("dev.comfyfluffy.caustica.Frame")){cpu.add(e.getLong("elapsedNanos")/1e6);allocation.add((double)e.getLong("allocatedBytes"));}
   if(type.equals("dev.comfyfluffy.caustica.GpuStage")){
    double nanos=e.getFloat("timestampPeriodNanos");long id=e.getLong("frameId");long start=(long)(e.getLong("startTicks")*nanos),end=(long)(e.getLong("endTicks")*nanos);
    if(end>=start){var envelope=gpu.computeIfAbsent(id,k->new long[]{Long.MAX_VALUE,0});envelope[0]=Math.min(envelope[0],start);envelope[1]=Math.max(envelope[1],end);}
    stages.computeIfAbsent(e.getString("stage"),k->new ArrayList<>()).add(e.getLong("elapsedNanos")/1e6);
   }
   if(type.equals("halo.CausticaFrame")){
    update.add(e.getDuration().toNanos()/1e6);int n=e.getInt("instances");minInstances=Math.min(minInstances,n);maxInstances=Math.max(maxInstances,n);
    last=new long[]{e.getLong("geometryUploadBytes"),e.getLong("textureUploadBytes"),e.getLong("blasPreparations"),e.getLong("buffers"),e.getLong("images")};if(first==null)first=last.clone();
   }
  }}
  var gpuMs=new ArrayList<Double>();gpu.values().forEach(v->gpuMs.add((v[1]-v[0])/1e6));
  var out=new StringBuilder("{\"hostLoopMs\":").append(series(loops)).append(",\"causticaCpuMs\":").append(series(cpu)).append(",\"causticaAllocationBytes\":").append(series(allocation)).append(",\"haloUpdateMs\":").append(series(update)).append(",\"causticaGpuEnvelopeMs\":").append(series(gpuMs));
  if(first!=null){out.append(",\"instancesMin\":").append(minInstances).append(",\"instancesMax\":").append(maxInstances).append(",\"resourcesFirst\":").append(Arrays.toString(first)).append(",\"resourcesLast\":").append(Arrays.toString(last));}
  out.append(",\"gpuStagesMs\":{");boolean comma=false;for(var entry:stages.entrySet()){if(comma)out.append(',');comma=true;out.append('"').append(entry.getKey()).append("\":").append(series(entry.getValue()));}out.append("}}");
  // JSON has no NaN; an unavailable metric remains null.
  String result=out.toString().replace("NaN","null");Files.writeString(Path.of(args[1]),result);System.out.println("Analyzed "+loops.size()+" host loops, "+gpu.size()+" GPU frames, "+update.size()+" Halo updates");
 }
}
