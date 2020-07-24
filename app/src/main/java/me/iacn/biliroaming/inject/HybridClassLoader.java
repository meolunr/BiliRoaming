package me.iacn.biliroaming.inject;

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
        if (name != null && name.startsWith("com.bapis.bilibili")) {
            return hostParent.loadClass(name);
        }
        return super.findClass(name);
    }
}