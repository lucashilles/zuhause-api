package zuhause.sunrise;

import com.google.gson.Gson;
import java.text.SimpleDateFormat;
import java.util.Date;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import zuhause.bot.TelegramBot;
import zuhause.util.Config;
import zuhause.util.ServerLog;
import zuhause.ws.ApiArduino;

/**
 *
 * @author Eduardo Folly
 */
public class SunriseSunset implements Runnable {

    private static final transient OkHttpClient CLIENT = new OkHttpClient();
    private static final String URL = "http://api.sunrise-sunset.org/json?lat=-22.16&lng=-42.42&formatted=0";
    private static final Gson GSON = new Gson();
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
    private static final SimpleDateFormat LOC = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private static final ServerLog SERVERLOG = ServerLog.getInstance();
    private static final TelegramBot BOT = Config.getTelegramBot("zuhause_iot_bot");

    @Override
    public void run() {
        // TODO - Configurar latitude e longitude pelo banco de dados.

        String name = "frente";
        String pin = "4";

        while (true) {
            try {

                Date[] dates = new Date[4];

                /**
                 * Today
                 */
                Request requestToday = new Request.Builder()
                        .url(URL + "&date=today")
                        .build();

                Response responseToday = CLIENT.newCall(requestToday).execute();

                SunriseBase base = GSON.fromJson(responseToday.body().string(),
                        SunriseBase.class);

                dates[0] = SDF.parse(base.getResults().getSunrise());

                dates[1] = SDF.parse(base.getResults().getCivilTwilightEnd());

                /**
                 * Tomorrow
                 */
                Request requestTomorrow = new Request.Builder()
                        .url(URL + "&date=tomorrow")
                        .build();

                Response responseTomorrow = CLIENT.newCall(requestTomorrow).execute();

                base = GSON.fromJson(responseTomorrow.body().string(),
                        SunriseBase.class);

                dates[2] = SDF.parse(base.getResults().getSunrise());

                dates[3] = SDF.parse(base.getResults().getCivilTwilightEnd());

                /**
                 * Calc
                 */
                long now = System.currentTimeMillis();

                int useDate = -1;

                for (int i = 0; i < dates.length; i++) {
                    if (dates[i].getTime() - now > 0) {
                        useDate = i;
                        break;
                    }
                }

                if (useDate < 0) {
                    System.out.println("Erro na utilização de datas.");
                    break;
                }

                String msg = "Luz " + name + " programada para "
                        + (useDate % 2 == 1 ? "acender" : "apagar")
                        + " às " + LOC.format(dates[useDate]) + ".";

                SERVERLOG.msg(-1, msg);

                BOT.sendMessage(msg);

                new ApiArduino().acionarDigital(name, pin, (useDate % 2) == 0);

                Thread.sleep(dates[useDate].getTime() - now);

                new ApiArduino().acionarDigital(name, pin, (useDate % 2) == 1);

                msg = "A luz " + name + " foi "
                        + (useDate % 2 == 1 ? "acesa." : "apagada.");

                SERVERLOG.msg(-1, msg);

                BOT.sendMessage(msg);

            } catch (Exception ex) {
                ex.printStackTrace();
                try {
                    Thread.sleep(300000); // 5 min
                } catch (Exception ex1) {
                    ex1.printStackTrace();
                    break;
                }
            }
        }
    }

}
