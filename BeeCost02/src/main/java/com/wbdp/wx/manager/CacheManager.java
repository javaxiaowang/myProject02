package com.wbdp.wx.manager;

import java.util.HashMap;

/**
 * Created by wisedata005 on 2017/7/5.
 */
public class CacheManager {

    private static HashMap cacheMap = new HashMap();

    /**
     * 单实例构造方法
     */
    private CacheManager() {
        super();
    }

    /**
     * 得到缓存。同步静态方法
     * @param key
     * @return
     */
    public synchronized static Object getCache(String key) {
        return cacheMap.get(key);
    }

    /**
     * 判断是否存在缓存
     * @param key
     * @return
     */
    public synchronized static boolean hasCache(String key) {
        return cacheMap.containsKey(key);
    }

    /**
     * 清除全部缓存
     */
    public synchronized static void clearAll() {
        cacheMap.clear();
    }

    /**
     * 清除指定的缓存
     *
     * @param key
     */
    public synchronized static void clearOnly(String key) {
        cacheMap.remove(key);
    }

    /**
     * 载入缓存
     * @param key
     * @param obj
     */
    public synchronized static void putCache(String key, Object obj) {
        cacheMap.put(key, obj);
    }
}
