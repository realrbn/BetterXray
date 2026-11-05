package dev.betterxray.test.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatComponent.class)
public interface ChatAccess {
    @Accessor("allMessages") List<?> betterxrayTest$messages();
}
