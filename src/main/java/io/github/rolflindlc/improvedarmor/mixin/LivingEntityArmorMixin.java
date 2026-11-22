package io.github.rolflindlc.improvedarmor.mixin;

import io.github.rolflindlc.improvedarmor.config.ServerConfig;
import io.github.rolflindlc.improvedarmor.hook.ArmorHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces vanilla's two damage-reduction steps in {@code LivingEntity#actuallyHurt}:
 * armor (which now also folds in Resistance and Protection) and the "magic" step (which becomes a no-op
 * so those layers are not counted twice).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityArmorMixin {
    @Shadow
    protected abstract void hurtArmor(DamageSource source, float amount);

    @Inject(method = "getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At("HEAD"), cancellable = true)
    private void improvedarmor$flatArmor(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if (!ServerConfig.enabled()) {
            return;
        }
        // Vanilla wears the armor down before reducing the damage; keep that behaviour.
        if (ArmorHooks.armorApplies(source)) {
            this.hurtArmor(source, amount);
        }
        cir.setReturnValue(ArmorHooks.damageAfterArmor((LivingEntity) (Object) this, source, amount));
    }

    @Inject(method = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At("HEAD"), cancellable = true)
    private void improvedarmor$skipVanillaMagicStep(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if (ServerConfig.enabled()) {
            cir.setReturnValue(amount);
        }
    }
}
