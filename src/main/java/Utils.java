import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

class Utils
{
	public static BufferedImage stitchImages(DatabaseManager dm, List<CardDef> packCards) throws IOException
	{
		return stitchImages(dm, packCards.stream().map(def -> def.imageFilename).toArray(String[]::new));
	}
	
	public static BufferedImage stitchImages(DatabaseManager dm, String[] packCards) throws IOException
	{
		ArrayList<BufferedImage> packImages = new ArrayList<BufferedImage>();
		int mh = 0, tw = 0;
		for (String card : packCards)
		{
			System.out.println(card);
			if ((card.indexOf("/") != -1) || (card.indexOf("..") != -1))
				throw new RuntimeException("Haha yeah, no.");
			BufferedImage img = ImageIO.read(Paths.get(dm.settings.cardsFolder, card).toFile());
			mh = Math.max(mh,img.getHeight());
			tw += img.getWidth();
			packImages.add(img);
		}
		System.out.println("Gen combined");
		double scaleFactor = 1.0;
		if (tw > 1600)
		{
			scaleFactor = 1600.0/tw;
			tw = 1600;
			mh = (int)(mh*scaleFactor);
		}
		BufferedImage combined = new BufferedImage(tw,mh,3);
		int x = 0;
		for (BufferedImage image : packImages)
		{
			combined.getGraphics().drawImage(image, (int)(x*scaleFactor), (int)((mh/scaleFactor-image.getHeight())/2*scaleFactor), (int)(scaleFactor*image.getWidth()), (int)(scaleFactor*image.getHeight()), null);
			x += image.getWidth();
		}
		System.out.println(" . . .");
		return combined;
	}
}
