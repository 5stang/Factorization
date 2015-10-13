package factorization.truth.word;

import factorization.truth.DocViewer;
import factorization.truth.WordPage;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.EnumChatFormatting;

public class TextWord extends Word {
    public final String text;

    public TextWord(String text) {
        this.text = text;
    }
    
    @Override
    public String toString() {
        return text + " ==> " + getLink();
    }
    
    @Override
    public int getWidth(FontRenderer font) {
        if (font == null) return 0;
        return font.getStringWidth(text);
    }
    
    @Override
    public int draw(DocViewer page, int x, int y, boolean hover) {
        String t = style + text;
        int color = getLinkColor(page, hover);
        if (getLink() != null) {
            t = EnumChatFormatting.UNDERLINE + text;
        }
        page.getFont().drawString(t, x, y, color); // The return value of drawString isn't helpful.
        return page.getFont().getStringWidth(text);
    }
    
    @Override
    public int getPaddingAbove() {
        return 2;
    }
    
    @Override
    public int getPaddingBelow() {
        return WordPage.TEXT_HEIGHT;
    }
}
