import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class AppLifecycleListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("Prepaid Voice Call Charging - Application Starting...");
        Thread mscThread = new Thread(() -> {
            try {
                MSC.main(new String[0]);
            } catch (Exception e) {
                System.err.println("MSC thread error: " + e.getMessage());
            }
        });
        mscThread.setDaemon(true);
        mscThread.start();
        System.out.println("MSC server started in background daemon thread.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("Prepaid Voice Call Charging - Application Shutting Down...");
    }
}
