package tool;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

public class RegExTst {
    //    @Test
    //    public void test() throws Exception {
    //        String test = "/login/{username}/pass/123/{abc}";
    //
    //        List<String> ls = new ArrayList<String>();
    //
    //        Pattern pattern = Pattern.compile("\\{(.+?)\\}");
    //
    //        Matcher matcher = pattern.matcher(test);
    //        int last =0;
    //        while (matcher.find()) {
    //            String left=test.substring(last,matcher.start());
    //            ls.add(left);
    //            String find =matcher.group();
    //            ls.add(find);
    //            last=matcher.end();
    //            System.out.println(last+"left<===========>:" + left);
    //            System.out.println(last+"find<===========>:" + find);
    //        }
    //        if (last+1<test.length()-1){
    //            ls.add(test.substring(last));
    //        }
    //
    //        for (String string : ls) {
    //            System.out.println(string);
    //        }
    //
    //    }
    
//    @Test
//    public void testLevel() {
//        String  str = "主题  -level(demo_organization)";
//        Matcher mat = Pattern.compile("(?<=(-level)\\()[^\\)]+").matcher(str);//此处是中文输入的（）
//        while (mat.find()) {
//            System.out.println(mat.group());
//        }
//    }
    final static Pattern remoteBlockPatter =Pattern.compile(".+SentinelBlockException.+\\s+");
    @Test
    public void testSentinelBlockException() {
        String  str = "xxSentinelBlockException  : DegradeException ";
        Matcher matcher = remoteBlockPatter.matcher(str);
        
        while (matcher.find()) {
            
            System.out.println(matcher.group(0));
            System.out.println(matcher.groupCount());
        }
    }
}
