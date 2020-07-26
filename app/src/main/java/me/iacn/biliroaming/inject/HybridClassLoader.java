package me.iacn.biliroaming.inject;

import me.iacn.biliroaming.BuildConfig;

/**
 * Created by Meolunr on 2020/7/24
 * Email meolunr@gmail.com
 */
class HybridClassLoader extends ClassLoader {
    private ClassLoader hostParent;

    public HybridClassLoader(ClassLoader hostParent) {
        this.hostParent = hostParent;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (name != null) {
            if (name.startsWith("kotlin.") || name.startsWith("kotlinx.") || name.startsWith(BuildConfig.APPLICATION_ID)) {
                return super.findClass(name);
            }
            return hostParent.loadClass(name);
        }
        return super.findClass(name);
    }
}