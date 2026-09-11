package coordinator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import utils.Join;
import utils.Streams;
import java.io.BufferedWriter;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import apiJson.ApiJson;
import userMessages.Violation;
import core.AllLs;
import core.E.Literal;
import core.M;
import core.OtherPackages;
import core.TName;
import tools.Fs;
import tools.SourceOracle.Ref;

public interface OutputOracle{
  Path rootDir();
  default long baseApiStamp(){ return Fs.lastModified(rootDir().resolve("base.json")); }
  default long mapStamp(){ return Fs.lastModified(rootDir().resolve("_map.json")); }
  default long pkgApiStamp(String pkg){ return Fs.lastModified(rootDir().resolve(pkg+".json")); }
  private Path builtPath(String pkg){ return rootDir().resolve(pkg+".built"); }
  default boolean stillBuilt(String pkg, List<Ref> files, long minMillis){
    return Fs.lastModified(builtPath(pkg)) >= minMillis && Fs.readUtf8(builtPath(pkg)).equals(OutputHelper.fileList(files));
  }
  default void commitBuilt(String pkg, List<Ref> files, long minExclusiveMillis){
    Fs.writeUtf8(builtPath(pkg), OutputHelper.fileList(files), minExclusiveMillis);
  }
  
  default void write(String path, Consumer<Consumer<String>> dataProducer){
    Fs.ofV(()->{try (BufferedWriter writer = Files.newBufferedWriter(rootDir().resolve(path))){
      dataProducer.accept(content -> Fs.ofV(()->writer.write(content)));
    }});
  }
  default OtherPackages addCachedPkgApi(OtherPackages other, String pkg){
    var path= rootDir().resolve(pkg+".json");
    var api= new OutputHelper().pgkApiFromJSon(path);
    if (api.isEmpty()){ throw Violation.cacheMissingPkgApiFile(path); }
    return other.mergeWith(api.get(), Math.max(other.stamp(), Fs.lastModified(path)));
  }//READS the pkg info and adds to other; Does not update the disk. Just reads info
  default OtherPackages startCachedPkgApi(String pkg,Map<String,Map<String,String>> map,long stamp){
    var path= rootDir().resolve(pkg+".json");
    var api= new OutputHelper().pgkApiFromJSon(path);
    if (api.isEmpty()){ throw Violation.cacheMissingPkgApiFile(path); }
    return OtherPackages.start(map,api.get(),stamp);
  }
  default long commitPkgApi(String pkg, List<Literal> core, long minExclusiveMillis){
    var path= rootDir().resolve(pkg+".json");
    var res= new OutputHelper().pgkApiFromJSon(path);
    if (res.isEmpty()){ return Fs.writeUtf8(path, ApiJson.toJSon(core),-1); }
    if (new OutputHelper().consistent(res.get(),core)){ return Fs.lastModified(path); }
    return Fs.writeUtf8(path, ApiJson.toJSon(core),minExclusiveMillis);
    }
  default long commitMap(Map<String,Map<String,String>> map, long minExclusiveMillis){
    var path= rootDir().resolve("_map.json");
    var res= new OutputHelper().mapFromJSon(path);
    if (res.isEmpty()){ return Fs.writeUtf8(path, new OutputHelper().toJSon(map),-1); }
    if (res.get().equals(map)){ return Fs.lastModified(path); }
    return Fs.writeUtf8(path, new OutputHelper().toJSon(map),minExclusiveMillis);
  }
  //commitMap only write if different from the old, and in that case it will bumps mtime strictly above minExclusiveMillis
}

class OutputHelper{
  static String fileList(List<Ref> files){ return Join.of(files.stream().map(Ref::fearPath).sorted(),"","\n",""); }
  String toJSon(Map<String,Map<String,String>> map){
    if (map.isEmpty()){ return "{}"; }
    return obj(map, m->obj(m, s->"\""+s+"\""));
  }
  Optional<Map<TName,Literal>> pgkApiFromJSon(Path p){
    if (!Fs.of(()->Files.exists(p))){ return Optional.empty(); }
    var s= Fs.readUtf8(p);
    var allowed= s.chars().allMatch(c -> Fs.allowed.indexOf(c) >= 0);
    if (!allowed){ throw Violation.cacheInvalidFile(p, "Non-whitelisted char"); }
    var out= new LimitedJsonParser(s, p).apiJsonToMap();
    return Optional.of(out);
  }
  private <T> String obj(Map<String,T> m, Function<T,String> v){
    return Join.of(m.entrySet().stream()
      .map(e->"\""+e.getKey()+"\":"+v.apply(e.getValue())),
      "{",",\n","}\n");//correctly throw for empty
  }
  Optional<Map<String,Map<String,String>>> mapFromJSon(Path p){
    if (!Fs.of(()->Files.exists(p))){ return Optional.empty(); }
    var s= Fs.readUtf8(p);
    var allowed= s.chars().allMatch(c -> Fs.allowed.indexOf(c) >= 0);
    if (!allowed){ throw Violation.cacheInvalidFile(p, "Non-whitelisted char"); }
    var out= new LimitedJsonParser(s,p).obj2();
    return Optional.of(out);
  }
  boolean consistent(Map<TName,Literal> map, List<Literal> core){
    var allCore= AllLs.of(core).values();
    if (map.size() != allCore.size()){ return false; }
    for (var l: allCore){
      //Not filtered to public-only: privates can still be mentioned in meth parameters and ret types.
      var cached= map.get(l.name());
      if (cached == null){ return false; }
      if (!eqApi(l, cached)){ return false; }
    }
    return true;
  }
  private static boolean eqApi(Literal a, Literal b){
    if (a.rc() != b.rc()){ return false; }
    if (!a.name().equals(b.name())){ return false; }
    if (!a.thisName().equals(b.thisName())){ return false; }
    if (!a.bs().equals(b.bs())){ return false; }
    if (!a.cs().equals(b.cs())){ return false; }
    return eqMs(a.ms(), b.ms());
  }
  private static boolean eqMs(List<M> a, List<M> b){
    if (a.size() != b.size()){ return false; }
    return Streams.zip(a,b).allMatch((ma,mb)->ma.sig().equals(mb.sig()));
  }
}