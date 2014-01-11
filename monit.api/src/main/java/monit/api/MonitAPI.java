package monit.api;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities.EscapeMode;
import org.jsoup.select.Elements;

public class MonitAPI {

	public static void main(String[] args) throws JSONException, Exception {
		MonitAPI ap = new MonitAPI("http://10.28.0.185:2812", "admin", "monit");
		// System.out.println(ap.getMonitServerStatus());
		JSONArray processes = ap.getProcesses();
		System.out.println(ap.getDetail(processes.getJSONObject(0).getString(
				"DetailURL")));
		//ap.enableMonitor(processes.getJSONObject(0).getString("DetailURL"));
		ap.disableMonitor(processes.getJSONObject(0).getString("DetailURL"));
	}

	OutputSettings setting = new OutputSettings();
	private String url;
	private String user;
	private String pass;

	public MonitAPI(String monitURL, String user, String pass) {
		setting.escapeMode(EscapeMode.xhtml);
		this.url = monitURL;
		this.user = user;
		this.pass = pass;
	}

	/**
	 * 传入通过getProcesses里面得到的DetailURL来停止监视某个进程
	 * 
	 * @param detailURL
	 * @throws Exception
	 */
	public void disableMonitor(String detailURL) throws Exception {
		JSONObject detail = this.getDetail(detailURL);
		if (detail.getString("Status").equals("Not monitored")) {
			throw new Exception("Service:" + detailURL + " Status is:"
					+ detail.getString("Status")
					+ ", it has already been unmonitorred!");
		}
		this.post(detailURL, "action", "unmonitor");
	}

	/**
	 * 传入通过getProcesses里面得到的DetailURL来重新开始监视某个进程
	 * 
	 * @param detailURL
	 * @throws Exception
	 */
	public void enableMonitor(String detailURL) throws Exception {
		JSONObject detail = this.getDetail(detailURL);
		if (!detail.getString("Status").equals("Not monitored")) {
			throw new Exception("Service:" + detailURL + " Status is:"
					+ detail.getString("Status")
					+ ", it has already been in the process of monitoring!");
		}
		this.post(detailURL, "action", "monitor");
	}

	/**
	 * 获得某个监视进程的详细情况 传入通过getProcesses里面得到的DetailURL
	 * 
	 * @param detailURL
	 * @return
	 * @throws IOException
	 */
	public JSONObject getDetail(String detailURL) throws IOException {
		JSONObject obj = new JSONObject();
		String str = this.get(detailURL);
		str = str.replaceAll("&nbsp;", " ");
		Document parse = Jsoup.parse(str);
		Elements select = parse.select("table[id=status-table]");
		if (select.size() > 0) {
			Element element = select.get(0);
			Elements tr = element.select("tbody>tr");
			for (Element row : tr) {
				Elements td = row.select("td");
				if (td.size() == 2) {
					obj.put(td.get(0).text(), td.get(1).text());
				}
			}
		}
		return obj;
	}

	/**
	 * 输出一个JSONArray，例子如下： [{"Status":"Online with all services","Protocol(s)":
	 * "[DEFAULT] at port 2812","Host":"server185","DetailURL":"server185"}]
	 * 
	 * @return
	 * @throws IOException
	 */
	public JSONArray getProcesses() throws IOException {
		String str = this.get("");
		str = str.replaceAll("&nbsp;", " ");
		// System.out.println(str);
		Document parse = Jsoup.parse(str);
		Elements select = parse.select("table[id=header-row]");
		JSONArray arr = new JSONArray();
		if (select.size() > 1) {
			Element element = select.get(1);
			Elements tr = element.select("tbody>tr");
			for (Element row : tr) {
				Elements td = row.select("td");
				if (td.size() == 3) {
					JSONObject obj = new JSONObject();
					obj.put("Host", td.get(0).text());
					obj.put("DetailURL", td.get(0).select("a").attr("href"));
					obj.put("Status", td.get(1).text());
					obj.put("Protocol(s)", td.get(2).text());
					arr.put(obj);
				}
			}
		}
		return arr;

	}

	public JSONObject getMonitServerStatus() throws IOException {
		String str = this.get("");
		str = str.replaceAll("&nbsp;", " ");
		// System.out.println(str);
		Document parse = Jsoup.parse(str);
		Elements select = parse.select("table[id=header-row]");
		Element element = select.get(0);
		JSONObject result = new JSONObject();
		Elements tr = element.select("tbody>tr");
		Elements tdhead = tr.get(0).select("th");
		Elements tdrow = tr.get(1).select("td");
		int i = 0;
		for (Element head : tdhead) {
			result.put(head.text(), tdrow.get(i++).text());
		}
		return result;
	}

	private String post(String uri, String key, String value)
			throws IOException {
		URL url = new URL((this.url.endsWith("/") ? this.url : this.url + "/")
				+ uri);
		String encoding = Base64.encodeBytes((user + ":" + pass).getBytes());
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		// connection.setRequestMethod("POST");
		connection.setRequestMethod("POST");
		// connection.setRequestProperty(key, value);
		connection.setRequestProperty("Content-Type",
				"application/x-www-form-urlencoded");
		connection.setDoOutput(true);
		connection.setDoInput(true);
		connection.setRequestProperty("Authorization", "Basic " + encoding);
		String outConet = "";
		DataOutputStream out = new DataOutputStream(
				connection.getOutputStream());
		outConet = key + "=" + value;
		out.writeBytes(outConet);
		out.flush();
		out.close();
		InputStream content = (InputStream) connection.getInputStream();
		BufferedReader in = new BufferedReader(new InputStreamReader(content));
		String line = "";
		String ret = "";
		while ((line = in.readLine()) != null) {
			ret += line;
			// System.out.println(line);
		}
		in.close();
		connection.disconnect();
		return ret;
	}

	private String get(String uri) throws IOException {
		URL url = new URL((this.url.endsWith("/") ? this.url : this.url + "/")
				+ uri);
		String encoding = Base64.encodeBytes((user + ":" + pass).getBytes());
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		// connection.setRequestMethod("POST");
		connection.setRequestMethod("GET");
		connection.setDoOutput(true);
		connection.setRequestProperty("Authorization", "Basic " + encoding);
		InputStream content = (InputStream) connection.getInputStream();
		BufferedReader in = new BufferedReader(new InputStreamReader(content));
		String line = "";
		String ret = "";
		while ((line = in.readLine()) != null) {
			ret += line;
			// System.out.println(line);
		}
		in.close();
		connection.disconnect();
		return ret;
	}
}
