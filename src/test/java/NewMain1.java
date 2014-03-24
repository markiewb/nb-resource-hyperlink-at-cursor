import java.math.BigDecimal;

public class NewMain1 {

    public static void main(String[] args) {
        System.out.println("filename = "
                + "com/toy/anagrams/lib/WordLibrary.java");
        System.out.println("filename = " + "WordLibrary.java");
        System.out.println("Hyper"); //no match
        System.out.println("Foo"); // no match
        System.out.println("main"); // 2 matches
        System.out.println("New"); // 2 matches
        System.out.println("foo/NewMain.java"); // 1 match
        System.out.println("de.markiewb.netbeans.plugins.resourcehyperlink.ResourceHyperlinkProvider");
        System.out.println("de.markiewb.netbeans.plugins.resourcehyperlink.ResourceHyperlinkProvider");
        System.out.println("D:\\ws\\nb-resource-hyperlink-at-cursor\\doc\\screenshot-1.0.0.png");
        System.out.println("D:/ws/nb-resource-hyperlink-at-cursor/doc/screenshot-1.0.0.png");
        System.out.println("filename = " + 1 + "help.png"
                + new BigDecimal("0"));
    }
}
