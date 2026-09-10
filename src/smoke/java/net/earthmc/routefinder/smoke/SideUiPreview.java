package net.earthmc.routefinder.smoke;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.earthmc.routefinder.integration.MapAddonBridge;

/** Render the actual released base column plus companion controls without a server login. */
public final class SideUiPreview extends Screen {
    public SideUiPreview(){super(Component.literal("Side controls preview"));}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xFF314C45);
        for(int x=0;x<width;x+=32)g.fill(x,0,x+1,height,0xFF46665C);
        for(int y=0;y<height;y+=32)g.fill(0,y,width,y+1,0xFF46665C);
        try{
            Object config=MapAddonBridge.call("getConfig");
            Class.forName("net.townymap.gui.MapToggleOverlay").getMethod("render",GuiGraphicsExtractor.class,int.class,config.getClass(),boolean.class,boolean.class).invoke(null,g,height,config,false,false);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    public static void settings(Minecraft mc){
        try{
            Class<?> type=Class.forName("net.townymap.gui.TownyMapConfigScreen");
            Screen screen=(Screen)type.getConstructor(Screen.class).newInstance(mc.gui.screen());
            mc.gui.setScreen(screen);
            var category=type.getDeclaredField("activeCategory");category.setAccessible(true);
            category.set(screen,Component.translatable("earthmcroutefinder.controls.settings").getString());
            var layout=type.getDeclaredMethod("relayout");layout.setAccessible(true);layout.invoke(screen);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
}
