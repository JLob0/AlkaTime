package com.alkacode.time.hook;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Chamadas reflexivas pro Citizens - nao e compileOnly aqui (sem artefato Maven publico
 * confiavel), mesmo racional documentado em AlkaVips/hook/HookReflection. Qualquer falha
 * (classe/metodo ausente, versao incompativel) cai no catch e vira log FINE, nunca propaga.
 */
final class HookReflection {

    private HookReflection() {
    }

    static Object invokeStatic(Logger logger, String className, String methodName,
                                Class<?>[] paramTypes, Object... args) {
        try {
            Class<?> clazz = Class.forName(className);
            Method method = clazz.getMethod(methodName, paramTypes);
            return method.invoke(null, args);
        } catch (Throwable t) {
            logger.log(Level.FINE, "Hook Citizens falhou (" + className + "#" + methodName + "): " + t, t);
            return null;
        }
    }

    static Object invokeInstance(Logger logger, Object target, String methodName,
                                  Class<?>[] paramTypes, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName, paramTypes);
            return method.invoke(target, args);
        } catch (Throwable t) {
            logger.log(Level.FINE, "Hook Citizens falhou (" + methodName + "): " + t, t);
            return null;
        }
    }
}
