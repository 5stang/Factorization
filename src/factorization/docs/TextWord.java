package factorization.docs;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.EnumChatFormatting;

public class TextWord extends Word {
    final String text;
    
    public TextWord(String text, String hyperlink) {
        super(hyperlink);
        this.text = text;
    }
    
    @Override
    public String toString() {
        return text + " ==> " + hyperlink;
    }
    
    @Override
    public int getWidth(FontRenderer font) {
        return font.getStringWidth(text);
    }
    
    @Override
    public int draw(DocViewer page, int x, int y) {
        String t = text;
        if (hyperlink != null) {
            t = "" + EnumChatFormatting.AQUA + EnumChatFormatting.UNDERLINE + text;
        }
        page.fontRenderer.drawString(t, x, y, 0xEEEEEE); // The return value of drawString isn't helpful.
        return page.fontRenderer.getStringWidth(text);
    }
}
