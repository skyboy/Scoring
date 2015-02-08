package cofh.scoring;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import java.util.Iterator;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class GuiGameOver extends net.minecraft.client.gui.GuiGameOver {

	private int field_146347_a;

	@Override
	public void initGui() {

		this.buttonList.clear();

		if (!Scoring.complete && this.mc.theWorld.getWorldInfo().isHardcoreModeEnabled()) {
			if (this.mc.isIntegratedServerRunning()) {
				this.buttonList.add(new GuiButton(1, this.width / 2 - 100, this.height / 4 + 96, I18n.format(
					"deathScreen.deleteWorld", new Object[0])));
			}
			else {
				this.buttonList.add(new GuiButton(1, this.width / 2 - 100, this.height / 4 + 96, I18n.format(
					"deathScreen.leaveServer", new Object[0])));
			}
		}
		else {
			this.buttonList.add(new GuiButton(0, this.width / 2 - 100, this.height / 4 + 72, I18n.format("deathScreen.respawn",
				new Object[0])));
			this.buttonList.add(new GuiButton(1, this.width / 2 - 100, this.height / 4 + 96, I18n.format(
				"deathScreen.titleScreen", new Object[0])));

			if (this.mc.getSession() == null) {
				((GuiButton) this.buttonList.get(1)).enabled = false;
			}
		}

		GuiButton guibutton;

		for (Iterator<GuiButton> iterator = this.buttonList.iterator(); iterator.hasNext(); guibutton.enabled = false) {
			guibutton = iterator.next();
		}
	}

	@Override
	protected void keyTyped(char p_73869_1_, int p_73869_2_) {

	}

	@Override
	protected void actionPerformed(GuiButton p_146284_1_) {

		switch (p_146284_1_.id) {
		case 0:
			this.mc.thePlayer.respawnPlayer();
			this.mc.displayGuiScreen((GuiScreen) null);
			break;
		case 1:
			GuiYesNo guiyesno = new GuiYesNo(this, I18n.format("deathScreen.quit.confirm", new Object[0]), "", I18n.format(
				"deathScreen.titleScreen", new Object[0]), I18n.format("deathScreen.respawn", new Object[0]), 0);
			this.mc.displayGuiScreen(guiyesno);
			guiyesno.func_146350_a(20);
		}
	}

	@Override
	public void confirmClicked(boolean p_73878_1_, int p_73878_2_) {

		if (p_73878_1_) {
			this.mc.theWorld.sendQuittingDisconnectingPacket();
			this.mc.loadWorld((WorldClient) null);
			this.mc.displayGuiScreen(new GuiMainMenu());
		}
		else {
			this.mc.thePlayer.respawnPlayer();
			this.mc.displayGuiScreen((GuiScreen) null);
		}
	}

	@Override
	public void drawScreen(int p_73863_1_, int p_73863_2_, float p_73863_3_) {

		super.drawGradientRect(0, 0, this.width, this.height, 1615855616, -1602211792);
		GL11.glPushMatrix();
		GL11.glScalef(2.0F, 2.0F, 2.0F);
		boolean flag = !Scoring.complete && this.mc.theWorld.getWorldInfo().isHardcoreModeEnabled();
		String s = Scoring.complete || flag ? I18n.format("deathScreen.title.hardcore", new Object[0]) : I18n.format(
			"deathScreen.title", new Object[0]);
		super.drawCenteredString(this.fontRendererObj, s, this.width / 2 / 2, 30, 16777215);
		GL11.glPopMatrix();

		if (flag) {
			super.drawCenteredString(this.fontRendererObj, I18n.format("deathScreen.hardcoreInfo", new Object[0]),
				this.width / 2, 144, 16777215);
		}

		super.drawCenteredString(this.fontRendererObj, I18n.format("deathScreen.score", new Object[0]) + ": " +
				EnumChatFormatting.YELLOW + Scoring.playerScore, this.width / 2, 100, 16777215);
		super.drawScreen(p_73863_1_, p_73863_2_, p_73863_3_);
	}

	@Override
	public void drawCenteredString(FontRenderer a, String b, int c, int d, int e) {

	}

	@Override
	protected void drawGradientRect(int a, int b, int c, int d, int e, int f) {

	}

	@Override
	public boolean doesGuiPauseGame() {

		return false;
	}

	@Override
	public void updateScreen() {

		super.updateScreen();
		++this.field_146347_a;
		GuiButton guibutton;

		if (this.field_146347_a == 20) {
			for (Iterator<GuiButton> iterator = this.buttonList.iterator(); iterator.hasNext(); guibutton.enabled = true) {
				guibutton = iterator.next();
			}
		}
	}
}
