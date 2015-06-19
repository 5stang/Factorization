package factorization.docs;

import net.minecraft.client.gui.FontRenderer;

public abstract class Word {
    private final String hyperlink;
    
    public Word(String hyperlink) {
        this.hyperlink = hyperlink;
    }
    
    public String getLink() {
        return hyperlink;
    }
    
    public abstract int getWidth(FontRenderer font);
    public abstract int draw(DocViewer doc, int x, int y, boolean hover);

    public void drawHover(DocViewer doc, int mouseX, int mouseY) { }
    
    public int getPaddingAbove() { return 1; }
    public int getPaddingBelow() { return 1; }
    
    public int getLinkColor(DocViewer doc, boolean hover) {
        boolean dark = doc.isDark();
        int color = dark ? 0xEEEEEE : 0x111111;
        if (getLink() != null) {
            if (hover) color = dark ? 0xFF0080 : 0x441111;
            else color = dark ? 0x00FFFF : 0x000080;
        }
        return color;
    }
}
