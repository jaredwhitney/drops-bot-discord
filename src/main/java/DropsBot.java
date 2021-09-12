import static java.nio.charset.StandardCharsets.UTF_8;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.TrayIcon.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileView;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import rip.$0k.utils.SysUtils;

public final class DropsBot
{
	public static final File DATABASE_LOCATION_FILE = new File("drops-db-path.cfg");
	
	public static DatabaseManager databaseManager;
	public static WebServer webServer;
	public static DiscordBot discordBot;
	public static TrayIcon runningIcon, nodbIcon, nobotIcon;
	public static JFileChooser fc;
	static String[] argStore;
	
	/**
	 * Main program entry point, starts both the web server and the discord bot
	 */
	public static void main(final String[] args) throws SQLException
	{
		
		System.out.println("*** drops? bot main method called ***");
		
		// Used for software-initiated restart
		argStore = args;
		
		// Determine the location of the database file
		String databaseLocation = null;
		if (args.length > 0)
		{
			databaseLocation = args[0];
		}
		else
		{
			databaseLocation = SysUtils.readTextFile(DATABASE_LOCATION_FILE, UTF_8);
			if (databaseLocation == null)
			{
				System.err.println("Couldn't figure out where you wanted the database to be stored; pass the file path as an argument or write it to \"" + DATABASE_LOCATION_FILE.getAbsolutePath() + "\".");
				setSystemTrayNoDatabase();
				fc = new JFileChooser(new File(".").getAbsolutePath());
				fc.setAcceptAllFileFilterUsed(false);
				fc.setApproveButtonText("Select DB file");
				fc.setDialogTitle("Select a location for the drops? Bot database file");
				fc.setFileFilter(new FileFilter(){
					@Override
					public boolean accept(File f)
					{
						return f.isDirectory() || f.getAbsolutePath().endsWith(".db");
					}
					public String getDescription()
					{
						return "Database files (.db)";
					}
				});
				fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
				fc.setMultiSelectionEnabled(false);
				int status = fc.showSaveDialog(null);
				System.out.println("JFileChooser returned status " + status);
				if (status != JFileChooser.APPROVE_OPTION)
					return;
				File file = fc.getSelectedFile();
				if (!file.getAbsolutePath().endsWith(".db"))
					file = new File(file.getAbsolutePath() + ".db");
				int remember = JOptionPane.showOptionDialog(null, "Should I remember this database file? (This involves creating a file at " + DATABASE_LOCATION_FILE.getAbsolutePath() + " pointing to the database file.)\nIf not, consider starting the app with the database file location as the first argument in the future.", "drops? Bot Setup", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, new String[]{"YES, remember it", "NO, I want to handle this myself"}, "YES, remember it");
				if (remember == 0)
					SysUtils.writeFile(DATABASE_LOCATION_FILE, file.getAbsolutePath(), UTF_8);
				else if (remember == 1)
					argStore = new String[]{file.getAbsolutePath()};
				else if (remember == JOptionPane.CLOSED_OPTION)
					return;
				restart();
				return;
			}
			databaseLocation = databaseLocation.trim();
		}
		
		// Start the database manager
		databaseManager = new DatabaseManager();
		databaseManager.connectToDatabase(databaseLocation);
		databaseManager.initAllTables();
		databaseManager.readAllFromDatabase();
		
		// Start the web server
		webServer = new WebServer(databaseManager);
		webServer.start();
		
		// Create the system tray entry
		setSystemTrayRunning();
		
		// Start the discord bot
		discordBot = new DiscordBot(databaseManager, webServer);
		discordBot.start();
		
	}
	
	public static void removeSystemTrayIcons()
	{
		if (SystemTray.isSupported())
		{
			SystemTray systemTray = SystemTray.getSystemTray();
			try
			{
				systemTray.remove(runningIcon);
			}
			catch (Exception ex)
			{}
			try
			{
				systemTray.remove(nodbIcon);
			}
			catch (Exception ex)
			{}
			try
			{
				systemTray.remove(nobotIcon);
			}
			catch (Exception ex)
			{}
		}
	}
	
	public static void restart()
	{
		if (webServer != null)
			webServer.shutdown();
		if (discordBot != null)
			discordBot.shutdown();
		removeSystemTrayIcons();
		try
		{
			JOptionPane.getRootFrame().dispose();
		}
		catch (Exception ex)
		{}
		try
		{
			fc.cancelSelection();
		}
		catch (Exception ex)
		{}
		if (databaseManager != null)
			databaseManager.disconnect();
		try
		{
			webServer = null;
			discordBot = null;
			databaseManager = null;
			main(argStore);
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
		return;
	}
	
	public static void setSystemTrayRunning()
	{
		if (SystemTray.isSupported())
		{
			removeSystemTrayIcons();
			SystemTray systemTray = SystemTray.getSystemTray();
			PopupMenu popup = new PopupMenu();
			try
			{
				runningIcon = new TrayIcon(new ImageIcon(readResource("botprofile.png"), "drops? Bot").getImage());
				runningIcon.setImageAutoSize(true);
				runningIcon.setToolTip("drops? Bot");
				MenuItem webServerItem = new MenuItem("Web Server");
				MenuItem restartItem = new MenuItem("Restart");
				MenuItem shutdownItem = new MenuItem("Shutdown");
				popup.add(webServerItem);
				popup.addSeparator();
				popup.add(restartItem);
				popup.add(shutdownItem);
				runningIcon.setPopupMenu(popup);
				try
				{
					systemTray.add(runningIcon);
				}
				catch (Exception ex){}
				webServerItem.addActionListener(new ActionListener(){
					@Override
					public void actionPerformed(ActionEvent ev)
					{
						try
						{
							Desktop.getDesktop().browse(new URI("http://127.0.0.1:" + databaseManager.settings.serverPort));
						}
						catch (Exception ex){}
					}
				});
				restartItem.addActionListener(new ActionListener(){
					@Override
					public void actionPerformed(ActionEvent ev)
					{
						System.out.println("Restart initiated from system tray");
						restart();
					}
				});
				shutdownItem.addActionListener(new ActionListener(){
					@Override
					public void actionPerformed(ActionEvent ev)
					{
						System.out.println("Shutdown initiated from system tray");
						System.exit(0);
					}
				});
			}
			catch (Exception ex)
			{
				ex.printStackTrace();
			}
		}
	}
	
	public static void setSystemTrayNoDatabase()
	{
		if (SystemTray.isSupported())
		{
			removeSystemTrayIcons();
			SystemTray systemTray = SystemTray.getSystemTray();
			PopupMenu popup = new PopupMenu();
			try
			{
				nodbIcon = new TrayIcon(new ImageIcon(readResource("botprofile-disabled.png"), "drops? Bot").getImage());
				nodbIcon.setImageAutoSize(true);
				nodbIcon.setToolTip("drops? Bot [NO DATABASE]");
				MenuItem restartItem = new MenuItem("Restart");
				MenuItem shutdownItem = new MenuItem("Shutdown");
				popup.add(restartItem);
				popup.add(shutdownItem);
				nodbIcon.setPopupMenu(popup);
				try
				{
					systemTray.add(nodbIcon);
				}
				catch (Exception ex){}
				restartItem.addActionListener(new ActionListener(){
					@Override
					public void actionPerformed(ActionEvent ev)
					{
						System.out.println("Restart initiated from system tray");
						restart();
					}
				});
				shutdownItem.addActionListener(new ActionListener(){
					@Override
					public void actionPerformed(ActionEvent ev)
					{
						System.out.println("Shutdown initiated from system tray");
						System.exit(0);
					}
				});
			}
			catch (Exception ex)
			{
				ex.printStackTrace();
			}
		}
	}
	
	public static void notifyNoBotToken()
	{
		try
		{
			// Note: This action listener may not fire, this is up to whether or not Java implements firing
			//       'ActionEvent's on notification clicks, which at least on the build of Windows 10 / JVM version
			//       I'm running on, it doesn't.
			nobotIcon.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent ev)
				{
					try
					{
						Desktop.getDesktop().browse(new URI("http://127.0.0.1:" + databaseManager.settings.serverPort + "/admin/settings"));
					}
					catch(Exception ex){}
				}
			});
			nobotIcon.displayMessage("drops? Bot", "No discord bot token specified, please enter one in the web server settings page.", MessageType.NONE);
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
	}
	
	public static void setSystemTrayNoBot()
	{
		if (SystemTray.isSupported())
		{
			removeSystemTrayIcons();
			SystemTray systemTray = SystemTray.getSystemTray();
			PopupMenu popup = new PopupMenu();
			try
			{
				nobotIcon = new TrayIcon(new ImageIcon(readResource("botprofile-nobot.png"), "drops? Bot").getImage());
				nobotIcon.setImageAutoSize(true);
				nobotIcon.setToolTip("drops? Bot [WEB SERVER ONLY]");
				MenuItem webServerItem = new MenuItem("Web Server");
				MenuItem restartItem = new MenuItem("Restart");
				MenuItem shutdownItem = new MenuItem("Shutdown");
				popup.add(webServerItem);
				popup.addSeparator();
				popup.add(restartItem);
				popup.add(shutdownItem);
				nobotIcon.setPopupMenu(popup);
				try
				{
					systemTray.add(nobotIcon);
				}
				catch (Exception ex){}
				webServerItem.addActionListener(new ActionListener(){
					@Override
					public void actionPerformed(ActionEvent ev)
					{
						try
						{
							Desktop.getDesktop().browse(new URI("http://127.0.0.1:" + databaseManager.settings.serverPort));
						}
						catch (Exception ex){}
					}
				});
				restartItem.addActionListener(new ActionListener(){
					@Override
					public void actionPerformed(ActionEvent ev)
					{
						System.out.println("Restart initiated from system tray");
						restart();
					}
				});
				shutdownItem.addActionListener(new ActionListener(){
					@Override
					public void actionPerformed(ActionEvent ev)
					{
						System.out.println("Shutdown initiated from system tray");
						System.exit(0);
					}
				});
			}
			catch (Exception ex)
			{
				ex.printStackTrace();
			}
		}
	}
	
	/**
	 * Reads a resource from the JAR file into a String
	 */
	public static String readResourceToString(String resourceName) throws IOException
	{
		return new String(readResource(resourceName), UTF_8);
	}
	
	/**
	 * Reads a resource from the JAR file into a String
	 */
	public static BufferedImage readResourceToImage(String resourceName) throws IOException
	{
		ByteArrayInputStream bis = new ByteArrayInputStream(readResource(resourceName));
		return ImageIO.read(bis);
	}
	
	/**
	 * Reads a resource from the JAR file into a byte array
	 */
	public static byte[] readResource(String resourceName) throws IOException
	{
		System.out.println("Read " + resourceName + " from the jar file");
		InputStream in = DropsBot.class.getResourceAsStream(resourceName); 
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		in.transferTo(os);
		return os.toByteArray();
	}
}
