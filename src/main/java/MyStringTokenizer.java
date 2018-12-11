import java.util.StringTokenizer;

public class MyStringTokenizer {

   public static void main(String []args){
       StringTokenizer tokenizer = new StringTokenizer("www.baidu.com",".b");
       while(tokenizer.hasMoreElements()){
          System.out.println("Token: " + tokenizer.nextToken());
       }
   }
}
