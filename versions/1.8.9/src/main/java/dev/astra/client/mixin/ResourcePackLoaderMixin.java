package dev.astra.client.mixin;

import net.minecraft.client.resource.ResourcePackLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resource pack folders: packs inside sub-folders of resourcepacks/ (e.g. resourcepacks/PvP/...)
 * show up too, so you can sort lots of packs into folders. A folder counts as a pack when it has a
 * pack.mcmeta; any other folder is searched (up to 4 levels deep).
 */
@Mixin(ResourcePackLoader.class)
public abstract class ResourcePackLoaderMixin {

    @Shadow
    private File resourcePackDir;

    @Inject(method = "getResourcePacks", at = @At("RETURN"), cancellable = true)
    private void astra$packFolders(CallbackInfoReturnable<List<File>> cir) {
        if (resourcePackDir == null || !resourcePackDir.isDirectory()) {
            return;
        }
        List<File> all = new ArrayList<File>(cir.getReturnValue());
        File[] children = resourcePackDir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory() && !new File(child, "pack.mcmeta").isFile()) {
                astra$collect(child, all, 1);
            }
        }
        cir.setReturnValue(all);
    }

    private static void astra$collect(File dir, List<File> out, int depth) {
        File[] files = dir.listFiles();
        if (files == null || depth > 4) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                if (new File(f, "pack.mcmeta").isFile()) {
                    out.add(f);
                } else {
                    astra$collect(f, out, depth + 1);
                }
            } else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                out.add(f);
            }
        }
    }
}
