/*
* Copyright (c) 2009 David Roberts <d@vidr.cc>
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in
* all copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
* THE SOFTWARE.
*/

package maropu.lljvm.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javafx.util.Pair;

import maropu.lljvm.util.ReflectionUtils;

/**
 * Provides function pointers for methods.
 * 
 * @author  David Roberts
 */
public class Function {
    /** Set of registered classes */
    private static Set<String> registeredClasses = new HashSet<>();
    /** Map of function signatures to function pointers */
    private static Map<String, Long> functionPointers = new HashMap<>();
    /** Map of function pointers to Method objects */
    private static Map<Long, Method> functionObjects = new HashMap<>();

    /** Map of external functions to Method objects */
    private static Map<String, Pair<Long, Method>> externalFunctions = new HashMap<>();

    /** Map of external fields to getter Method objects */
    private static Map<String, Pair<Long, Method>> externalFieldGetters = new HashMap<>();

    static {
        for(Method method : ReflectionUtils.getStaticMethods(NumbaRuntime.class)) {
            // TODO: Reconsider this
            final long addr = method.hashCode();
            final String sig = ReflectionUtils.getSignature(method);
            method.setAccessible(true);
            externalFunctions.put(sig, new Pair<>(addr, method));
        }
    }

    /**
     * Prevent this class from being instantiated.
     */
    private Function() {}
    
    /**
     * If the class specified by the given binary name has not yet been
     * registered, then generate function pointers for all public static
     * methods that it declares.
     * 
     * @param classname  Binary name of the class to register.
     * @throws ClassNotFoundException
     *                   if no class with the given name can be found
     */
    private static void registerClass(String classname)
    throws ClassNotFoundException {
        // if(registeredClasses.contains(classname))
        //     return;
        Class<?> cls = ReflectionUtils.getClass(classname);
        for(Method method : ReflectionUtils.getStaticMethods(cls)) {
            final long addr = VMemory.allocateStack(1);
            final String sig = ReflectionUtils.getQualifiedSignature(method);
            functionPointers.put(sig, addr);
            functionObjects.put(addr, method);
            method.setAccessible(true);
        }
        registeredClasses.add(classname);
    }
    
    /**
     * Return a function pointer for the method with the specified signature
     * declared by the specified class.
     * 
     * @param classname        the binary name of the declaring class
     * @param methodSignature  the signature of the method
     * @return                 a function pointer for the specified method
     */
    public static long getFunctionPointer(String classname, String methodSignature) {
        try {
            registerClass(classname);
        } catch(ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        final String sig = classname + "/" + methodSignature;
        if(!functionPointers.containsKey(sig))
            throw new IllegalArgumentException(
                    "Unable to get function pointer for "+sig);
        return functionPointers.get(sig);
    }

    public static long getExternalFunctionPointer(String methodSignature) {
        if (externalFunctions.containsKey(methodSignature)) {
            final Pair<Long, Method> func = externalFunctions.get(methodSignature);
            if (!functionObjects.containsKey(func.getKey())) {
                functionObjects.put(func.getKey(), func.getValue());
            }
            return func.getKey();
        } else {
            throw new IllegalArgumentException(
                    "Cannot resolve an external function for " + methodSignature);
        }
    }

    public static long getExternalFieldGetterPointer(String fieldName) {
        if (externalFieldGetters.containsKey(fieldName)) {
            final Pair<Long, Method> func = externalFieldGetters.get(fieldName);
            if (!functionObjects.containsKey(func.getKey())) {
                functionObjects.put(func.getKey(), func.getValue());
            }
            return func.getKey();
        } else {
            throw new IllegalArgumentException(
                    "Cannot resolve an external field for" + fieldName);
        }
    }

    /**
     * Invoke the method pointed to by the given function pointer with the
     * given arguments.
     * 
     * @param f     the function pointer
     * @param args  a pointer to the packed list of arguments
     * @return      the return value of the method
     */
    private static Object invoke(long f, long args) {
        final Method method = functionObjects.get(f);
        if(method == null)
            throw new IllegalArgumentException("Invalid function pointer: "+f);
        final Class<?>[] paramTypes = method.getParameterTypes();
        try {
            if (args != 0) {
                final Object[] params = VMemory.unpack(args, paramTypes);
                return method.invoke(null, params);
            } else {
                return method.invoke(null, null);
            }
        } catch(IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch(InvocationTargetException e) {
            Throwable cause = e.getCause();
            if(cause instanceof RuntimeException)
                throw (RuntimeException) cause;
            throw new RuntimeException(cause);
        }
    }
    
    /**
     * Invoke the method pointed to by the given function pointer with the
     * given arguments.
     * 
     * @param f     the function pointer
     * @param args  a pointer to the packed list of arguments
     */
    public static void invoke_void(long f, long args) {
        invoke(f, args);
    }
    public static void invoke_void(long f) {
        invoke(f, 0);
    }

    /**
     * Invoke the method pointed to by the given function pointer with the
     * given arguments.
     * 
     * @param f     the function pointer
     * @param args  a pointer to the packed list of arguments
     * @return      the return value of the method
     */
    public static boolean invoke_i1(long f, long args) {
        return (Boolean) invoke(f, args);
    }
    
    /**
     * Invoke the method pointed to by the given function pointer with the
     * given arguments.
     * 
     * @param f     the function pointer
     * @param args  a pointer to the packed list of arguments
     * @return      the return value of the method
     */
    public static byte invoke_i8(long f, long args) {
        return (Byte) invoke(f, args);
    }
    
    /**
     * Invoke the method pointed to by the given function pointer with the
     * given arguments.
     * 
     * @param f     the function pointer
     * @param args  a pointer to the packed list of arguments
     * @return      the return value of the method
     */
    public static short invoke_i16(long f, long args) {
        return (Short) invoke(f, args);
    }
    
    /**
     * Invoke the method pointed to by the given function pointer with the
     * given arguments.
     * 
     * @param f     the function pointer
     * @param args  a pointer to the packed list of arguments
     * @return      the return value of the method
     */
    public static int invoke_i32(long f, long args) {
        return (Integer) invoke(f, args);
    }
    
    /**
     * Invoke the method pointed to by the given function pointer with the
     * given arguments.
     * 
     * @param f     the function pointer
     * @param args  a pointer to the packed list of arguments
     * @return      the return value of the method
     */
    public static long invoke_i64(long f, long args) {
        return (Long) invoke(f, args);
    }
    
    /**
     * Invoke the method pointed to by the given function pointer with the
     * given arguments.
     * 
     * @param f     the function pointer
     * @param args  a pointer to the packed list of arguments
     * @return      the return value of the method
     */
    public static float invoke_f32(long f, long args) {
        return (Float) invoke(f, args);
    }
    
    /**
     * Invoke the method pointed to by the given function pointer with the
     * given arguments.
     * 
     * @param f     the function pointer
     * @param args  a pointer to the packed list of arguments
     * @return      the return value of the method
     */
    public static double invoke_f64(long f, long args) {
        return (Double) invoke(f, args);
    }
}