package com.i2i_systems.drools.kjar.creator;

import static org.appformer.maven.integration.embedder.MavenProjectLoader.parseMavenPom;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.maven.model.DeploymentRepository;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.appformer.maven.integration.embedder.MavenEmbedder;
import org.appformer.maven.integration.embedder.MavenProjectLoader;
import org.appformer.maven.integration.embedder.MavenSettings;
import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.drools.compiler.kproject.ReleaseIdImpl;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.scanner.KieMavenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KjarCreator {
	final Logger logger = LoggerFactory.getLogger(KjarCreator.class);
	private static KieMavenRepository repository;

	static KieServices ks;
	static ReleaseId releaseId;
	static KieFileSystem kfs;
	static InternalKieModule kieModule;
	static File pomFile;
	static byte[] pom;
	static byte[] kjarValue;
	static File kjar;

	public static void createKjar() throws IOException {
		pomFile = new File("conf/pom.xml");
		pom = Files.readString(Paths.get("conf/pom.xml")).getBytes();
		kfs = ks.newKieFileSystem().writePomXML(pom);
		kfs.write("src/main/resources/r0.drl", Files.readString(Paths.get("conf/testRule.drl")));

		KieBuilder kb = ks.newKieBuilder(kfs).buildAll();
		if (kb.getResults().hasMessages(Message.Level.ERROR)) {
			for (Message result : kb.getResults().getMessages()) {
				System.out.println(result.getText());
			}
		}
		kieModule = (InternalKieModule) ks.getRepository().getKieModule(releaseId);
		kjarValue = kieModule.getBytes();
	}

	@SuppressWarnings("resource")
	public static void main(String args[]) throws FileNotFoundException, IOException, URISyntaxException {

		ks = KieServices.Factory.get();
		releaseId = new ReleaseIdImpl("com.i2i_systems.kjarcreator", "creationtest", "0.0.1");

		try {
			createKjar();
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			kjar = new File("dist/creationtest.jar");
			kjar.createNewFile(); // if file already exists will do nothing
			FileOutputStream stream = new FileOutputStream(kjar, false);
			stream.write(kjarValue);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try {
			deployKJar();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void deployKJar() throws URISyntaxException, IOException {

		KieMavenRepository repository = getRepository(ks, kfs, releaseId, pom);
		
		RemoteRepository remRepository = getRemoteRepositoryFromDistributionManagement(pomFile);
		if (remRepository == null) {
			System.out.println("No Distribution Management configured: unknown repository");
			return;
		}
		repository.deployArtifact(remRepository, releaseId, kjar, pomFile);
	}

	public static KieMavenRepository getRepository(KieServices ks, KieFileSystem kfs, ReleaseId releaseId, byte[] pom) {
		if (repository == null)
			return KieMavenRepository
					.getKieMavenRepository(MavenProjectLoader.parseMavenPom(new ByteArrayInputStream(pom)));
		return repository;
	}

	protected static RemoteRepository getRemoteRepositoryFromDistributionManagement(File pomFile) {
		MavenProject mavenProject = parseMavenPom(pomFile);
		DistributionManagement distMan = mavenProject.getDistributionManagement();
		if (distMan == null) {
			return null;
		}
		DeploymentRepository deployRepo = distMan.getSnapshotRepository() != null
				&& mavenProject.getVersion().endsWith("SNAPSHOT") ? distMan.getSnapshotRepository()
						: distMan.getRepository();
		if (deployRepo == null) {
			return null;
		}

		RemoteRepository.Builder remoteRepoBuilder = new RemoteRepository.Builder(deployRepo.getId(),
				deployRepo.getLayout(), deployRepo.getUrl())
						.setSnapshotPolicy(new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_DAILY,
								RepositoryPolicy.CHECKSUM_POLICY_WARN))
						.setReleasePolicy(new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS,
								RepositoryPolicy.CHECKSUM_POLICY_WARN));

		Server server = MavenSettings.getSettings().getServer(deployRepo.getId());
		if (server != null) {
			MavenEmbedder embedder = MavenProjectLoader.newMavenEmbedder(false);
			try {
				Authentication authentication = embedder.getMavenSession().getRepositorySession()
						.getAuthenticationSelector().getAuthentication(remoteRepoBuilder.build());
				remoteRepoBuilder.setAuthentication(authentication);
			} finally {
				embedder.dispose();
			}
		}

		return remoteRepoBuilder.build();
	}
}
