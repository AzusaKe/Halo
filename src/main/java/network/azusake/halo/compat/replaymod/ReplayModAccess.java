package network.azusake.halo.compat.replaymod;

import java.lang.reflect.*;
import java.util.function.Consumer;

/** Optional public API access, including event interfaces relocated by ReplayMod's distribution build. */
final class ReplayModAccess {
    private ReplayModAccess(){}
    static Class<?> type(String name)throws ReflectiveOperationException{return Class.forName(name);}
    static Object singleton(String name)throws ReflectiveOperationException{return type(name).getField("instance").get(null);}
    static Object call(Object target,String name,Object...args)throws ReflectiveOperationException{
        for(Method method:target.getClass().getMethods())if(method.getName().equals(name) && compatible(method.getParameterTypes(),args))
            { method.trySetAccessible(); return method.invoke(target,args); }
        throw new NoSuchMethodException(target.getClass().getName()+"."+name);
    }
    static Object invokeStatic(String owner,String name,Object...args)throws ReflectiveOperationException{
        for(Method method:type(owner).getMethods())if(method.getName().equals(name) && compatible(method.getParameterTypes(),args))
            return method.invoke(null,args);
        throw new NoSuchMethodException(owner+"."+name);
    }
    private static boolean compatible(Class<?>[] parameters,Object[] args){
        if(parameters.length!=args.length)return false;
        for(int i=0;i<args.length;i++)if(args[i]!=null && !(parameters[i].isInstance(args[i])
            || parameters[i]==boolean.class && args[i] instanceof Boolean || parameters[i]==int.class && args[i] instanceof Integer
            || parameters[i]==long.class && args[i] instanceof Long))return false;
        return true;
    }
    static void event(String owner,Consumer<Object[]> listener)throws ReflectiveOperationException{
        Class<?> callback=type(owner); Object event=callback.getField("EVENT").get(null);
        Object proxy=Proxy.newProxyInstance(callback.getClassLoader(),new Class<?>[]{callback},(p,m,args)->{
            if(m.getDeclaringClass()==Object.class)return switch(m.getName()){
                case "toString"->"Halo replay callback";case "hashCode"->System.identityHashCode(p);case "equals"->p==args[0];default->null;};
            listener.accept(args==null?new Object[0]:args);return null;
        });
        // Event.register itself is package-private; EventRegistrations is the public registration API.
        Class<?> registrations=type(event.getClass().getPackageName()+".EventRegistrations");
        Object registration=registrations.getConstructor().newInstance();
        call(registration,"on",event,proxy);
        call(registration,"register");
    }
}
