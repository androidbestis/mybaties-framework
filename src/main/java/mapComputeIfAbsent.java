import java.util.HashMap;
import java.util.Map;

public class mapComputeIfAbsent {

    static  Map<String,String> stringMap = new HashMap<String,String>();

    public static void main(String [] args){
        //jdk1.8特性
        stringMap.computeIfAbsent("lixudong",K -> new String("adonai"));
        stringMap.computeIfAbsent("myXXX",k-> new String("it“is"));
        stringMap.computeIfAbsent("heart slow down",k->"calm");

        for(Map.Entry<String, String> sub : stringMap.entrySet()){
            System.out.println(sub);
        }
    }
}
