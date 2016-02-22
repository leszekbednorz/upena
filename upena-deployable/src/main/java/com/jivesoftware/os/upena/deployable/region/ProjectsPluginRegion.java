package com.jivesoftware.os.upena.deployable.region;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import com.jivesoftware.os.upena.deployable.region.ProjectsPluginRegion.ProjectsPluginRegionInput;
import com.jivesoftware.os.upena.deployable.soy.SoyRenderer;
import com.jivesoftware.os.upena.service.UpenaStore;
import com.jivesoftware.os.upena.shared.PathToRepo;
import com.jivesoftware.os.upena.shared.Project;
import com.jivesoftware.os.upena.shared.ProjectFilter;
import com.jivesoftware.os.upena.shared.ProjectKey;
import com.jivesoftware.os.upena.shared.TimestampedValue;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.io.FileUtils;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.TextProgressMonitor;

/**
 *
 */
// soy.page.projectsPluginRegion
public class ProjectsPluginRegion implements PageRegion<ProjectsPluginRegionInput> {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final String template;
    private final String outputTemplate;
    private final SoyRenderer renderer;
    private final UpenaStore upenaStore;
    private final PathToRepo localPathToRepo;

    private final Map<ProjectKey, AtomicLong> runningProjects = new ConcurrentHashMap<>();
    private final ExecutorService projectExecutors = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public ProjectsPluginRegion(String template,
        String outputTemplate,
        SoyRenderer renderer,
        UpenaStore upenaStore,
        PathToRepo localPathToRepo
    ) {
        this.template = template;
        this.outputTemplate = outputTemplate;
        this.renderer = renderer;
        this.upenaStore = upenaStore;
        this.localPathToRepo = localPathToRepo;
    }

    @Override
    public String getRootPath() {
        return "/ui/projects";
    }

    public static class ProjectsPluginRegionInput implements PluginInput {

        final String key;
        final String name;
        final String description;
        final String localPath;
        final String scmUrl;
        final String branch;

        final String pom;
        final String goals;

        final String mvnHome;
        final String action;

        public ProjectsPluginRegionInput(String key,
            String name,
            String description,
            String localPath,
            String scmUrl,
            String branch,
            String pom,
            String goals,
            String mvnHome,
            String action) {

            this.key = key;
            this.name = name;
            this.description = description;
            this.localPath = localPath;
            this.scmUrl = scmUrl;
            this.branch = branch;
            this.pom = pom;
            this.goals = goals;
            this.mvnHome = mvnHome;

            this.action = action;
        }

        @Override
        public String name() {
            return "Projects";
        }

    }

    @Override
    public String render(String user, ProjectsPluginRegionInput input) {
        Map<String, Object> data = Maps.newHashMap();

        try {

            Map<String, String> filters = new HashMap<>();
            filters.put("name", input.name);
            filters.put("description", input.description);

            data.put("filters", filters);

            ProjectFilter filter = new ProjectFilter(null, null, 0, 10000);
            if (input.action != null) {
                ProjectKey projectKey = new ProjectKey(input.key);
                if (input.action.equals("filter")) {
                    filter = new ProjectFilter(
                        input.name.isEmpty() ? null : input.name,
                        input.description.isEmpty() ? null : input.description,
                        0, 10000);
                    data.put("message", "Filtering: name.contains '" + input.name + "' description.contains '" + input.description + "'");
                } else if (input.action.equals("build")) {
                    filters.clear();
                    File repoFile = localPathToRepo.get();

                    try {
                        Project project = upenaStore.projects.get(projectKey);
                        if (project == null) {
                            data.put("message", "Couldn't checkout no existent project. Someone else likely just removed it since your last refresh.");
                        } else {
                            AtomicLong running = runningProjects.computeIfAbsent(projectKey, (k) -> new AtomicLong());
                            if (running.compareAndSet(0, System.currentTimeMillis())) {

                                File root = new File(project.localPath);
                                FileUtils.forceMkdir(root);
                                File localPath = new File(root, project.name);
                                File output = new File(root, project.name + "-output.txt");
                                FileUtils.deleteQuietly(localPath);
                                output.delete();

                                FileOutputStream fos = new FileOutputStream(output);
                                PrintStream ps = new PrintStream(fos);
                                ps.println("FILE: Wiped " + localPath);
                                ps.println("FILE: Wiped " + output);
                                ps.println();
                                ps.println("COMMAND: Starting the build....");
                                ps.flush();

                                projectExecutors.submit(() -> {

                                    try {

                                        ps.println("GIT: Cloning from " + project.scmUrl + " to " + localPath);

                                        // then clone
                                        try (Git git = Git.cloneRepository()
                                            .setURI(project.scmUrl)
                                            .setDirectory(localPath)
                                            .setProgressMonitor(new TextProgressMonitor(new PrintWriter(ps)))
                                            .call()) {

                                            ps.println("COMMAND: Checking out branch " + project.branch);
                                            git.checkout().
                                                setCreateBranch(true).
                                                setName(project.branch).
                                                setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).
                                                setStartPoint("origin/" + project.branch).
                                                call();

                                            try {
                                                ps.println("COMMAND: Invoking maven with these goals " + project.goals);
                                                List<String> goals = Lists.newArrayList(Splitter.on(' ').split(input.goals));
                                                InvocationRequest request = new DefaultInvocationRequest();
                                                request.setPomFile(new File(localPath, input.pom));
                                                request.setGoals(goals);
                                                request.setOutputHandler((line) -> {
                                                    ps.println(line);
                                                });
                                                request.setMavenOpts("-Xmx3000m");

                                                Invoker invoker = new DefaultInvoker();

                                                FileUtils.forceMkdir(repoFile);

                                                invoker.setLocalRepositoryDirectory(repoFile);
                                                invoker.setMavenHome(new File(input.mvnHome));
                                                InvocationResult result = invoker.execute(request);

                                                if (result.getExitCode() == 0) {
                                                    ps.println();
                                                    ps.println("Hooray it builds!");
                                                    ps.println();

                                                    ps.println("Invoking deploying... ");

                                                    request = new DefaultInvocationRequest();
                                                    request.setPomFile(new File(localPath, input.pom));
                                                    request.setGoals(Arrays.asList("javadoc:jar", "source:jar", "deploy"));
                                                    request.setOutputHandler((line) -> {
                                                        ps.println(line);
                                                    });
                                                    request.setShowErrors(true);
                                                    request.setShowVersion(true);

                                                    request.setMavenOpts("-Xmx3000m");
                                                    request.setDebug(false);

                                                    Properties properties = new Properties();

                                                    String repoUrl = "http://localhost:1175/repo";
                                                    properties.setProperty("altDeploymentRepository", "mine::default::" + repoUrl);
                                                    properties.setProperty("altReleaseRepository", "mine::default::" + repoUrl);
                                                    properties.setProperty("altSnapshotRepository", "mine::default::" + repoUrl);

                                                    properties.setProperty("skipTests", "true");
                                                    properties.setProperty("deployAtEnd", "true");
                                                    request.setProperties(properties);
                                                    request.addShellEnvironment(user, user);

                                                    invoker = new DefaultInvoker();
                                                    invoker.setLocalRepositoryDirectory(repoFile);
                                                    invoker.setMavenHome(new File("/usr/local/Cellar/maven/3.3.1/libexec"));
                                                    result = invoker.execute(request);

                                                    if (result.getExitCode() == 0) {
                                                        ps.println();
                                                        ps.println("SUCCES: Hooray it deployed!");
                                                        ps.println();
                                                    } else {
                                                        ps.println();
                                                        ps.println("ERROR: Darn it!");
                                                        ps.println();
                                                    }
                                                } else {
                                                    ps.println();
                                                    ps.println("ERROR: Darn it!");
                                                    ps.println();
                                                }
                                            } catch (Exception x) {
                                                String trace = x.getMessage() + "\n" + Joiner.on("\n").join(x.getStackTrace());
                                                ps.println(trace);
                                                ps.println("error while calling mvn " + trace);
                                            }

                                            fos.flush();
                                            fos.close();
                                        } catch (Exception x) {
                                            String trace = x.getMessage() + "\n" + Joiner.on("\n").join(x.getStackTrace());
                                            ps.println(trace);
                                            fos.flush();
                                            fos.close();
                                        }
                                    } catch (Exception x) {
                                        LOG.error("Unexpected failure while building" + project, x);
                                    } finally {
                                        runningProjects.remove(projectKey);
                                    }
                                });

                                return output(input.key);
                            } else {
                                data.put("message", "Project is alreadying running.");
                            }
                        }
                    } catch (Exception x) {
                        String trace = x.getMessage() + "\n" + Joiner.on("\n").join(x.getStackTrace());
                        data.put("message", "Error while trying to build Project:" + input.name + "\n" + trace);
                        LOG.error("JGit eror", x);
                    }

                } else if (input.action.equals("add")) {
                    filters.clear();
                    try {
                        Project newProject = new Project(input.name,
                            input.description,
                            input.localPath,
                            input.scmUrl,
                            input.branch,
                            input.pom,
                            input.goals,
                            input.mvnHome,
                            new HashSet<>(),
                            new HashSet<>(),
                            new ArrayList<>());
                        upenaStore.projects.update(null, newProject);
                        upenaStore.record(user, "added", System.currentTimeMillis(), "", "projects-ui", newProject.toString());

                        data.put("message", "Created Project:" + input.name);
                    } catch (Exception x) {
                        String trace = x.getMessage() + "\n" + Joiner.on("\n").join(x.getStackTrace());
                        data.put("message", "Error while trying to add Project:" + input.name + "\n" + trace);
                    }
                } else if (input.action.equals("update")) {
                    filters.clear();
                    try {
                        Project project = upenaStore.projects.get(projectKey);
                        if (project == null) {
                            data.put("message", "Couldn't update no existent project. Someone else likely just removed it since your last refresh.");
                        } else {
                            Project updatedProject = new Project(input.name,
                                input.description,
                                input.localPath,
                                input.scmUrl,
                                input.branch,
                                input.pom,
                                input.goals,
                                input.mvnHome,
                                new HashSet<>(),
                                new HashSet<>(),
                                new ArrayList<>());
                            upenaStore.projects.update(projectKey, updatedProject);
                            data.put("message", "Updated Project:" + input.name);
                            upenaStore.record(user, "updated", System.currentTimeMillis(), "", "projects-ui", project.toString());
                        }
                    } catch (Exception x) {
                        String trace = x.getMessage() + "\n" + Joiner.on("\n").join(x.getStackTrace());
                        data.put("message", "Error while trying to add Project:" + input.name + "\n" + trace);
                    }
                } else if (input.action.equals("remove")) {
                    if (input.key.isEmpty()) {
                        data.put("message", "Failed to remove Project:" + input.name);
                    } else {
                        try {
                            Project removing = upenaStore.projects.get(projectKey);
                            if (removing != null) {
                                upenaStore.projects.remove(projectKey);
                                upenaStore.record(user, "removed", System.currentTimeMillis(), "", "projects-ui", removing.toString());
                            }
                        } catch (Exception x) {
                            String trace = x.getMessage() + "\n" + Joiner.on("\n").join(x.getStackTrace());
                            data.put("message", "Error while trying to remove Project:" + input.name + "\n" + trace);
                        }
                    }
                }
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            Map<ProjectKey, TimestampedValue<Project>> found = upenaStore.projects.find(filter);
            for (Map.Entry<ProjectKey, TimestampedValue<Project>> entrySet : found.entrySet()) {
                ProjectKey key = entrySet.getKey();
                TimestampedValue<Project> timestampedValue = entrySet.getValue();
                Project value = timestampedValue.getValue();

                Map<String, Object> row = new HashMap<>();
                row.put("running", runningProjects.containsKey(key));
                row.put("key", key.getKey());
                row.put("name", value.name);
                row.put("description", value.description);
                row.put("localPath", value.localPath);
                row.put("scmUrl", value.scmUrl);
                row.put("branch", value.branch);
                row.put("pom", value.pom);
                row.put("goals", value.goals);
                row.put("mvnHome", value.mvnHome);
                rows.add(row);
            }

            Collections.sort(rows, (Map<String, Object> o1, Map<String, Object> o2) -> {
                String projectName1 = (String) o1.get("name");
                String projectName2 = (String) o2.get("name");

                int c = projectName1.compareTo(projectName2);
                if (c != 0) {
                    return c;
                }
                return c;
            });

            data.put("projects", rows);

        } catch (Exception e) {
            LOG.error("Unable to retrieve data", e);
        }

        return renderer.render(template, data);
    }

    public String output(String key) throws Exception {
        Map<String, Object> data = Maps.newHashMap();

        List<String> log = new ArrayList<>();

        Project project = upenaStore.projects.get(new ProjectKey(key));
        if (project != null) {
            data.put("key", key);
            data.put("name", project.name);

            File output = new File(new File(project.localPath), project.name + "-output.txt");
            if (output.exists()) {
                List<String> lines = FileUtils.readLines(output);
                log.addAll(lines);
            }

        } else {
            data.put("key", key);
            data.put("name", "No project found for " + key);
        }
        data.put("log", log);

        return renderer.render(outputTemplate, data);
    }

    public void add(String user, ProjectUpdate releaseGroupUpdate) throws Exception {
        ProjectKey projectKey = new ProjectKey(releaseGroupUpdate.projectId);
        Project project = upenaStore.projects.get(projectKey);
        if (project != null) {
            //project.defaultReleaseGroups.put(new ServiceKey(releaseGroupUpdate.serviceId), new ReleaseGroupKey(releaseGroupUpdate.releaseGroupId));
            //upenaStore.projects.update(projectKey, project);
            //upenaStore.record(user, "updated", System.currentTimeMillis(), "", "projects-ui", project.toString());
        }
    }

    public void remove(String user, ProjectUpdate releaseGroupUpdate) throws Exception {
        ProjectKey projectKey = new ProjectKey(releaseGroupUpdate.projectId);
        Project project = upenaStore.projects.get(projectKey);
        if (project != null) {
            //if (project.defaultReleaseGroups.remove(new ServiceKey(releaseGroupUpdate.serviceId)) != null) {
            //    upenaStore.projects.update(projectKey, project);
            //    upenaStore.record(user, "updated", System.currentTimeMillis(), "", "projects-ui", project.toString());
            //}
        }
    }

    public static class ProjectUpdate {

        public String projectId;
        public String serviceId;
        public String releaseGroupId;

        public ProjectUpdate() {
        }

        public ProjectUpdate(String projectId, String serviceId, String releaseGroupId) {
            this.projectId = projectId;
            this.serviceId = serviceId;
            this.releaseGroupId = releaseGroupId;
        }

    }

    @Override
    public String getTitle() {
        return "Projects";
    }

}