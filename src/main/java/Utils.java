import java.awt.image.BufferedImage;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

class Utils
{
	public static BufferedImage unknownBorderImage = null;
	public static BufferedImage ironBorderImage = null;
	public static BufferedImage bronzeBorderImage = null;
	public static BufferedImage silverBorderImage = null;
	public static BufferedImage goldBorderImage = null;
	public static BufferedImage diamondBorderImage = null;
	public static BufferedImage unknownEmblemImage = null;
	public static BufferedImage oneStarEmblemImage = null;
	public static BufferedImage twoStarEmblemImage = null;
	public static BufferedImage threeStarEmblemImage = null;
	public static BufferedImage fourStarEmblemImage = null;
	
	static
	{
		try
		{
			unknownBorderImage = DropsBot.readResourceToImage("card-border-unknown.png");
			ironBorderImage = DropsBot.readResourceToImage("card-border-iron.png");
			bronzeBorderImage = DropsBot.readResourceToImage("card-border-bronze.png");
			silverBorderImage = DropsBot.readResourceToImage("card-border-silver.png");
			goldBorderImage = DropsBot.readResourceToImage("card-border-gold.png");
			diamondBorderImage = DropsBot.readResourceToImage("card-border-diamond.png");
			unknownEmblemImage = DropsBot.readResourceToImage("card-emblem-unknown.png");
			oneStarEmblemImage = DropsBot.readResourceToImage("card-emblem-one-star.png");
			twoStarEmblemImage = DropsBot.readResourceToImage("card-emblem-two-star.png");
			threeStarEmblemImage = DropsBot.readResourceToImage("card-emblem-three-star.png");
			fourStarEmblemImage = DropsBot.readResourceToImage("card-emblem-four-star.png");
		}
		catch (Exception ex)
		{
			System.out.println("Bundled resource load failed.");
			ex.printStackTrace();
		}
	}
	
	public static BufferedImage getCardImage(DatabaseManager dm, CardDef def) throws IOException
	{
		BufferedImage img = ImageIO.read(Paths.get(dm.settings.cardsFolder, def.imageFilename).toFile());
		return getCardImage(img, -1, -1, Math.max(img.getHeight(), 560));
	}
	
	public static BufferedImage getCardImage(DatabaseManager dm, CardInst inst) throws IOException
	{
		BufferedImage img = ImageIO.read(Paths.get(dm.settings.cardsFolder, inst.def.imageFilename).toFile());
		return getCardImage(img, inst.level, inst.stars, Math.max(img.getHeight(), 560));
	}
	
	public static BufferedImage getCardImage(BufferedImage image, int level, int stars, int desiredHeight)
	{
		Graphics2D g2 = (Graphics2D)image.getGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		if (image.getWidth() > image.getHeight()*0.6)
		{
			if (image.getHeight() < desiredHeight/2)
			{
				BufferedImage tmp = image;
				image = new BufferedImage((int)(tmp.getWidth()*((desiredHeight/2.0)/tmp.getHeight())), desiredHeight/2, BufferedImage.TYPE_INT_ARGB);
				g2.dispose();
				g2 = (Graphics2D)image.getGraphics();
				g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g2.drawImage(tmp, (image.getWidth()-tmp.getWidth())/2, (image.getHeight()-tmp.getHeight())/2, null);
			}
			BufferedImage tmp = image;
			image = new BufferedImage((int)(tmp.getHeight()*0.6), tmp.getHeight(), BufferedImage.TYPE_INT_ARGB);
			g2.dispose();
			g2 = (Graphics2D)image.getGraphics();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.drawImage(tmp, -(tmp.getWidth()-image.getWidth())/2, 0, null);
		}
		else if (image.getWidth() < image.getHeight()*0.6)
		{
			BufferedImage tmp = image;
			image = new BufferedImage((int)(tmp.getHeight()*0.6), tmp.getHeight(), BufferedImage.TYPE_INT_ARGB);
			g2.dispose();
			g2 = (Graphics2D)image.getGraphics();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.drawImage(tmp, (image.getWidth()-tmp.getWidth())/2, (image.getHeight()-tmp.getHeight())/2, null);
		}
		BufferedImage tmp = image;
		image = new BufferedImage((int)(image.getWidth()/(image.getHeight()/(double)desiredHeight)), desiredHeight, BufferedImage.TYPE_INT_ARGB);
		g2 = (Graphics2D)image.getGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.drawImage(tmp, 0, 0, image.getWidth(), image.getHeight(), null);
		g2.drawImage(getBorderImage(level), 0, 0, image.getWidth(), image.getHeight(), null);
		BufferedImage emblemImage = getEmblemImage(stars);
		double emblemScale = (emblemImage.getHeight()/(double)desiredHeight);
		g2.drawImage(emblemImage, (int)(image.getWidth()-emblemImage.getWidth()/emblemScale)/2, (int)(image.getHeight()-emblemImage.getHeight()/emblemScale), (int)(emblemImage.getWidth()/emblemScale), (int)(emblemImage.getHeight()/emblemScale), null);
		g2.dispose();
		return image;
	}
	
	public static BufferedImage getBorderImage(int level)
	{
		if (level < 0)
			return unknownBorderImage;
		if (level < 25)
			return ironBorderImage;
		if (level < 50)
			return bronzeBorderImage;
		if (level < 75)
			return silverBorderImage;
		if (level < 100)
			return goldBorderImage;
		return diamondBorderImage;
	}
	
	public static BufferedImage getEmblemImage(int stars)
	{
		if (stars < 0)
			return unknownEmblemImage;
		if (stars == 1)
			return oneStarEmblemImage;
		if (stars == 2)
			return twoStarEmblemImage;
		if (stars == 3)
			return threeStarEmblemImage;
		if (stars == 4)
			return fourStarEmblemImage;
		// TODO: Need a better way to handle this?
		return unknownEmblemImage;
	}
	
	public static BufferedImage stitchImages(DatabaseManager dm, List<CardDef> packCards) throws IOException
	{
		return stitchImages(dm, packCards.stream().map(def -> def.imageFilename).toArray(String[]::new));
	}
	
	public static BufferedImage stitchImages(DatabaseManager dm, String[] packCards) throws IOException
	{
		if (dm.settings.cardsFolder.length() == 0)
			return null;
		ArrayList<BufferedImage> packImages = new ArrayList<BufferedImage>();
		int mh = 0;
		for (String card : packCards)
		{
			System.out.println(card);
			if ((card.indexOf("/") != -1) || (card.indexOf("..") != -1))
				throw new RuntimeException("Haha yeah, no.");
			Path path = Paths.get(dm.settings.cardsFolder, card);
			if (!path.startsWith(Paths.get(dm.settings.cardsFolder).toAbsolutePath()))
				throw new RuntimeException("Haha yeah, no.");
			BufferedImage img = ImageIO.read(path.toFile());
			mh = Math.max(mh,img.getHeight());
			packImages.add(img);
		}
		int tw = 0;
		for (int j = 0; j < packImages.size(); j++)
		{
			BufferedImage image = packImages.get(j);
			image = getCardImage(image, -1, -1, mh);
			packImages.set(j, image);
			tw += image.getWidth();
		}
		System.out.println("Gen combined");
		BufferedImage combined = new BufferedImage(tw,mh,3);
		int x = 0;
		for (BufferedImage image : packImages)
		{
			combined.getGraphics().drawImage(image, x, 0, null);
			x += image.getWidth();
		}
		System.out.println(" . . .");
		return combined;
	}
}
