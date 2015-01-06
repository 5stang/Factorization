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
}
