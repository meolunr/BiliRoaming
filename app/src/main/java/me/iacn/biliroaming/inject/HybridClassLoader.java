package me.iacn.biliroaming.inject;

import me.iacn.biliroaming.BuildConfig;

/**
 * Created by Meolunr on 2020/7/24
 * Email meolunr@gmail.com
 */
class HybridClassLoader extends ClassLoader {

    private ClassLoader mModuleParent;
    private ClassLoader mHostParent;

    public HybridClassLoader(ClassLoader moduleParent, ClassLoader hostParent) {
        this.mModuleParent = moduleParent;
        this.mHostParent = hostParent;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (name != null) {
            if (name.startsWith("kotlin.") || name.startsWith("kotlinx.") || name.startsWith(BuildConfig.APPLICATION_ID)) {
                return super.findClass(name);
            } else if (name.startsWith("de.robv.android.xposed.")) {
                return mModuleParent.loadClass(name);
            } else {
                return mHostParent.loadClass(name);
            }
        }
        return super.findClass(name);
    }
}