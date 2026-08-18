package com.acorp.jvminsight.cluster.kubernetes;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public final class KubernetesSslContextFactory {

  private KubernetesSslContextFactory() {}

  public static SSLContext create(Path caPath) {

    try (InputStream input = Files.newInputStream(caPath)) {

      CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

      Certificate certificate = certificateFactory.generateCertificate(input);

      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

      keyStore.load(null, null);

      keyStore.setCertificateEntry("kubernetes-ca", certificate);

      TrustManagerFactory trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

      trustManagerFactory.init(keyStore);

      SSLContext sslContext = SSLContext.getInstance("TLS");

      sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

      return sslContext;

    } catch (Exception ex) {

      throw new IllegalStateException("Failed creating Kubernetes SSL context", ex);
    }
  }
}
