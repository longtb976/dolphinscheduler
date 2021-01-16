package org.apache.dolphinscheduler.remote.rpc.client;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;

import org.apache.dolphinscheduler.remote.config.NettyClientConfig;
import org.apache.dolphinscheduler.remote.rpc.base.Rpc;
import org.apache.dolphinscheduler.remote.rpc.remote.NettyClient;

import java.util.concurrent.ConcurrentHashMap;

/**
 * RpcClient
 */
public class RpcClient implements IRpcClient{

    private ConcurrentHashMap<String,Object> classMap=new ConcurrentHashMap<>();

    @Override
    public <T> T create(Class<T> clazz) throws Exception {
        if(!classMap.containsKey(clazz.getName())){
            T proxy = new ByteBuddy()
                .subclass(clazz)
                .method(isDeclaredBy(clazz)).intercept(MethodDelegation.to(new ConsumerInterceptor(new ConsumerInvoker())))
                .make()
                .load(getClass().getClassLoader())
                .getLoaded()
                .getDeclaredConstructor().newInstance();

            classMap.putIfAbsent(clazz.getName(),proxy);
        }
        return (T) classMap.get(clazz.getName());
    }
}
