package in.govtjobs.collect;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.util.Properties;

/** SMTP send used by the daily alert. Tests pass their own {@link AlertMail.Mailer}. */
final class SmtpMailer implements AlertMail.Mailer {

    @Override
    public void send(AlertMail.Mail mail) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.host", mail.host());
        props.put("mail.smtp.port", Integer.toString(mail.port()));
        boolean auth = mail.user() != null && !mail.user().isBlank();
        props.put("mail.smtp.auth", Boolean.toString(auth));
        props.put("mail.smtp.ssl.enable", Boolean.toString(mail.secure()));
        props.put("mail.smtp.starttls.enable", Boolean.toString(!mail.secure()));
        props.put("mail.smtp.connectiontimeout", "2000");
        props.put("mail.smtp.timeout", "2000");
        props.put("mail.smtp.writetimeout", "2000");
        Session session = Session.getInstance(props, auth ? new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(mail.user(), mail.password() == null ? "" : mail.password());
            }
        } : null);
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(mail.from()));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(mail.to(), false));
        message.setSubject(mail.subject(), "UTF-8");
        MimeBodyPart text = new MimeBodyPart();
        text.setText(mail.text() == null ? "" : mail.text(), "UTF-8");
        MimeBodyPart html = new MimeBodyPart();
        html.setContent(mail.html() == null ? "" : mail.html(), "text/html; charset=UTF-8");
        MimeMultipart parts = new MimeMultipart("alternative");
        parts.addBodyPart(text);
        parts.addBodyPart(html);
        message.setContent(parts);
        Transport.send(message);
    }
}
