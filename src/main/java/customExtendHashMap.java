import org.apache.ibatis.binding.BindingException;

import java.util.HashMap;

public class customExtendHashMap {


    //自扩展HashMap
    public static class StrictMap<V> extends HashMap<String,V> {

        private static final long serialVersionUID = -5741767162221585340L;

        //重写需要扩展的方法Method
        @Override
        public V get(Object key) {
            if(!super.containsKey(key)){        //调用父类--也就是HashMap中的方法
                throw new RuntimeException("Parameter '" + key + "' not found. Available parameters are " + this.keySet());
            }
            return super.get(key);
        }
    }


}
