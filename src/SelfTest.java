/**
 * Created by JOKER on 4/15/16.
 */
public class SelfTest {
    public static void main(String[] args) {
        String s = "\'ahaahahadfdfdf\'";
        s = s.substring(s.indexOf("'")+1,s.lastIndexOf("'"));
        System.out.println(s);
    }
}
