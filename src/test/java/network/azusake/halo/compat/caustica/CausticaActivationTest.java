package network.azusake.halo.compat.caustica;

import dev.comfyfluffy.caustica.minecraft.api.MinecraftApi;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CausticaActivationTest {
    private static final String PROPERTY="halo.caustica.experimental";

    @Test void absentOptionalModKeepsOrdinaryFrameHooksLoadableByDefault() throws Exception {
        assertArrayEquals(new boolean[]{true,false},probe(null,false,"withoutCaustica"));
    }

    @Test void extensionRegistersAutomaticallyAndExplicitOptOutPreventsRegistration() throws Exception {
        assertArrayEquals(new boolean[]{true,true},probe(null,true,"registration"));
        assertArrayEquals(new boolean[]{false,false},probe("false",true,"registration"));
    }

    private boolean[] probe(String setting,boolean installed,String method) throws Exception {
        String previous=System.getProperty(PROPERTY);
        try {
            if(setting==null)System.clearProperty(PROPERTY);else System.setProperty(PROPERTY,setting);
            URL[] paths={CausticaActivation.class.getProtectionDomain().getCodeSource().getLocation(),
                    getClass().getProtectionDomain().getCodeSource().getLocation()};
            try(var loader=new URLClassLoader(paths,getClass().getClassLoader()) {
                @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                    synchronized(getClassLoadingLock(name)) {
                        if(!installed && (name.startsWith("dev.comfyfluffy.caustica.") || name.startsWith("org.lwjgl.vulkan.")))
                            throw new ClassNotFoundException("Optional backend unavailable: "+name);
                        if(!name.startsWith("network.azusake.halo.compat.caustica."))return super.loadClass(name,resolve);
                        Class<?> type=findLoadedClass(name);
                        if(type==null)type=findClass(name);
                        if(resolve)resolveClass(type);
                        return type;
                    }
                }
            }) {
                return (boolean[])Class.forName(Probe.class.getName(),true,loader).getMethod(method).invoke(null);
            }
        } finally {
            if(previous==null)System.clearProperty(PROPERTY);else System.setProperty(PROPERTY,previous);
        }
    }

    /** Direct bytecode calls reproduce ordinary hooks without reflective resolution of optional signatures. */
    public static final class Probe {
        public static boolean[] withoutCaustica() {
            CausticaBridge.beginCapture();
            CausticaBridge.finishCapture(false);
            CausticaBridge.vanillaFrame();
            CausticaBridge.resourcesChanged();
            return new boolean[]{CausticaActivation.ENABLED,CausticaBridge.usingRt()};
        }
        public static boolean[] registration() {
            var registered=new AtomicInteger();
            new CausticaExtension().registerMinecraft(new MinecraftApi(factory -> {
                registered.incrementAndGet();
                return () -> { };
            }));
            return new boolean[]{CausticaActivation.ENABLED,registered.get()==1};
        }
    }
}
