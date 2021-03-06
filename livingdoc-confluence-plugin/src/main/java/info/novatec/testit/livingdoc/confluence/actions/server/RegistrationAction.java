package info.novatec.testit.livingdoc.confluence.actions.server;

import com.atlassian.confluence.pages.Page;
import info.novatec.testit.livingdoc.confluence.LivingDocServerConfigurationActivator;
import info.novatec.testit.livingdoc.confluence.macros.LivingDocPage;
import info.novatec.testit.livingdoc.confluence.velocity.LivingDocConfluenceManager;
import info.novatec.testit.livingdoc.server.LivingDocServerException;
import info.novatec.testit.livingdoc.server.domain.*;
import info.novatec.testit.livingdoc.server.domain.component.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static info.novatec.testit.livingdoc.confluence.utils.HtmlUtils.stringSetToTextArea;


/**
 * Action for the <code>LivingDoc Server Properties</code>.
 * <p></p>
 * Copyright (c) 2006 Pyxis technologies inc. All Rights Reserved.
 */
@SuppressWarnings("serial")
public class RegistrationAction extends LivingDocServerAction {
    private static final Logger log = LoggerFactory.getLogger(RegistrationAction.class);
    private Boolean readonly = Boolean.FALSE;

    private List<Runner> runners;
    private LinkedList<Project> projects;
    private List<SystemUnderTest> systemUnderTests;

    private SystemUnderTest selectedSut;
    private String selectedSutName;

    private String repositoryName;
    private String newProjectName;
    private String username;
    private String pwd;
    private String baseUrl;

    private String newSutName = "";
    private String newFixtureFactory;
    private String newFixtureFactoryArgs;
    private String newRunnerName = "";
    private Boolean selected = Boolean.FALSE;
    private String newProjectDependencyDescriptor;

    private String sutClasspath;

    private String fixtureClasspath;

    private boolean addMode;
    private boolean editMode;
    private boolean editPropertiesMode;
    private boolean editClasspathsMode;
    private boolean editFixturesMode;

    private List<Page> pagesToMigrate;
    private List<Page> migratedPages;

    public RegistrationAction(LivingDocConfluenceManager confluenceLivingDoc, LivingDocServerConfigurationActivator livingDocServerConfigurationActivator) {
        super(confluenceLivingDoc, livingDocServerConfigurationActivator);
    }

    public RegistrationAction(){
        super(null, null);
    }

    public String doGetRegistration() {
        log.debug("Getting registration status");
        if (!isServerReady()) {
            log.error("registration failed because server is not ready");
            addActionError(LivingDocConfluenceManager.SERVER_NOCONFIGURATION);
            readonly = true;
            editMode = false;
            addMode = false;
            return SUCCESS;
        } else {
            try {
                if (StringUtils.isEmpty(projectName))
                    projectName = getRegisteredRepository().getProject().getName();
                if (StringUtils.isEmpty(repositoryName))
                    repositoryName = getRegisteredRepository().getName();
                if (StringUtils.isEmpty(username))
                    username = getRegisteredRepository().getUsername();
                if (StringUtils.isEmpty(pwd))
                    pwd = getRegisteredRepository().getPassword();

                checkRepositoryBaseUrl();
                doGetSystemUnderTests();
                log.debug("Getting registration status succeeded");
            } catch (LivingDocServerException e) {
                if (editMode && StringUtils.isEmpty(projectName) && !isWithNewProject())
                    projectName = getProjects().getLast().getName();
                readonly = true;
                log.error("Getting registration status failed");
            }
            return editMode ? doGetSystemUnderTests() : SUCCESS;
        }
    }

    public String doRegister() {
        try {
            log.debug("Starting registratioin");

            if (getUsername() != null) {
                getLivingDocConfluenceManager().verifyCredentials(getUsername(), getPwd());
            }

            String uid = getLivingDocConfluenceManager().getSettingsManager().getGlobalSettings().getSiteTitle() + "-" + getSpaceKey();
            registeredRepository = Repository.newInstance(uid);
            registeredRepository.setProject(getProjectForRegistration());
            registeredRepository.setType(RepositoryType.newInstance("CONFLUENCE"));
            registeredRepository.setName(repositoryName);
            registeredRepository.setContentType(ContentType.TEST);
            registeredRepository.setBaseUrl(getBaseUrl());
            registeredRepository.setBaseRepositoryUrl(repositoryBaseUrl());
            registeredRepository.setBaseTestUrl(newTestUrl());
            registeredRepository.setUsername(getUsername());
            registeredRepository.setPassword(getPwd());

            getPersistenceService().registerRepository(registeredRepository);
            projectName = isWithNewProject() ? newProjectName : projectName;
            log.debug("Registration successfull");

        } catch (LivingDocServerException e) {
            addActionError(e);
            editMode = true;
            readonly = true;
            log.error("Registration failed",e);
        }

        return doGetRegistration();
    }

    public String doUpdateRegistration() {
        try {
            log.debug("Starting registration update");

            if (getUsername() != null) {
                getLivingDocConfluenceManager().verifyCredentials(getUsername(), getPwd());
            }

            String uid = getLivingDocConfluenceManager().getSettingsManager().getGlobalSettings().getSiteTitle() + "-" + getSpaceKey();
            Repository newRepository = Repository.newInstance(uid);
            newRepository.setProject(getProjectForRegistration());
            newRepository.setType(RepositoryType.newInstance("CONFLUENCE"));
            newRepository.setName(repositoryName);
            newRepository.setContentType(ContentType.TEST);
            newRepository.setBaseUrl(getBaseUrl());
            newRepository.setBaseRepositoryUrl(repositoryBaseUrl());
            newRepository.setBaseTestUrl(newTestUrl());
            newRepository.setUsername(getUsername());
            newRepository.setPassword(getPwd());

            getPersistenceService().updateRepositoryRegistration(newRepository);
            projectName = isWithNewProject() ? newProjectName : projectName;
            log.debug("Registration updated successfully");
        } catch (LivingDocServerException e) {
            addActionError(e);
            editMode = true;
            readonly = true;
            log.error("Registration update failed",e);
        }

        return doGetRegistration();
    }

    public String doGetSystemUnderTests() {
        try {
            log.debug("Getting system under tests");
            runners = getPersistenceService().getAllRunners();
            if (runners.isEmpty())
                throw new LivingDocServerException("livingdoc.suts.norunners", "No runners.");

            systemUnderTests = getPersistenceService().getSystemUnderTestsOfProject(projectName);
            if (selectedSut == null) {
                for (SystemUnderTest sut : systemUnderTests) {
                    if (sut.getName().equals(selectedSutName)) {
                        selectedSut = sut;
                        return SUCCESS;
                    }
                    if (sut.isDefault()) {
                        selectedSut = sut;
                    }
                }

                if (selectedSut != null)
                    selectedSutName = selectedSut.getName();
            }
            log.debug("Successfully updated SUT´s");
        } catch (LivingDocServerException e) {
            addActionError(e);
            addMode = false;
            log.error("Error getting SUT´s",e);
        }

        return SUCCESS;
    }

    public String doAddSystemUnderTest() {
        try {
            selectedSut = SystemUnderTest.newInstance(newSutName);
            selectedSut.setProject(Project.newInstance(projectName));

            selectedSut.setFixtureFactory(newFixtureFactory);
            selectedSut.setFixtureFactoryArgs(newFixtureFactoryArgs);
            selectedSut.setIsDefault(selected);
            selectedSut.setRunner(Runner.newInstance(newRunnerName));
            selectedSut.setProjectDependencyDescriptor(newProjectDependencyDescriptor);
            selectedSut.setSutClasspaths(ClasspathSet.parse(sutClasspath));

            getPersistenceService().createSystemUnderTest(selectedSut, getHomeRepository());
            successfullAction();
        } catch (LivingDocServerException e) {
            addActionError(e);
        }

        return doGetSystemUnderTests();
    }

    public String doUpdateSystemUnderTest() {
        try {

            SystemUnderTest newSut = SystemUnderTest.newInstance(selectedSutName);
            newSut.setProject(Project.newInstance(projectName));
            newSut = getPersistenceService().getSystemUnderTest(newSut, getHomeRepository());

            newSut.setName(newSutName);
            newSut.setFixtureFactory(newFixtureFactory);
            newSut.setFixtureFactoryArgs(newFixtureFactoryArgs);
            newSut.setRunner(Runner.newInstance(newRunnerName));
            newSut.setProjectDependencyDescriptor(newProjectDependencyDescriptor);
            newSut.setSutClasspaths(ClasspathSet.parse(sutClasspath));

            getPersistenceService().updateSystemUnderTest(selectedSutName, newSut, getHomeRepository());
            successfullAction();
            return doGetSystemUnderTests();
        } catch (LivingDocServerException e) {
            try {
                runners = getPersistenceService().getAllRunners();
                if (runners.isEmpty())
                    throw new LivingDocServerException("livingdoc.suts.norunners", "No runners.");

                systemUnderTests = getPersistenceService().getSystemUnderTestsOfProject(projectName);
                selectedSut = SystemUnderTest.newInstance(selectedSutName);
                selectedSut.setProject(Project.newInstance(projectName));
                selectedSut.setFixtureFactory(newFixtureFactory);
                selectedSut.setFixtureFactoryArgs(newFixtureFactoryArgs);
                selectedSut.setRunner(Runner.newInstance(newRunnerName));
                selectedSut.setProjectDependencyDescriptor(newProjectDependencyDescriptor);
            } catch (LivingDocServerException e1) {
                addActionError(e1);
            }

            addActionError(e);
        }

        return SUCCESS;
    }

    public String doRemoveSystemUnderTest() {
        try {
            selectedSut = SystemUnderTest.newInstance(selectedSutName);
            selectedSut.setProject(Project.newInstance(projectName));
            getPersistenceService().removeSystemUnderTest(selectedSut, getHomeRepository());
            selectedSutName = null;
        } catch (LivingDocServerException e) {
            addActionError(e);
        }

        selectedSut = null;
        return doGetSystemUnderTests();
    }

    public String doEditClasspath() {
        try {
            SystemUnderTest selectedSut = SystemUnderTest.newInstance(selectedSutName);
            selectedSut.setProject(Project.newInstance(projectName));
            selectedSut = getPersistenceService().getSystemUnderTest(selectedSut, getHomeRepository());
            selectedSut.setSutClasspaths(ClasspathSet.parse(sutClasspath));
            getPersistenceService().updateSystemUnderTest(selectedSutName, selectedSut, getHomeRepository());
        } catch (LivingDocServerException e) {
            addActionError(e);
        }

        return doGetSystemUnderTests();
    }

    public Set<String> getClasspaths() {
        return selectedSut.getSutClasspaths();
    }

    public String getClasspathsAsTextAreaContent() {
        return stringSetToTextArea(getClasspaths());
    }

    public String getClasspathTitle() {
        return getText("livingdoc.suts.classpath");
    }

    public String getFixtureClasspathTitle() {
        return getText("livingdoc.suts.fixture");
    }

    public Set<String> getFixtureClasspaths() {
        return selectedSut.getFixtureClasspaths();
    }

    public String getFixtureClasspathsAsTextAreaContent() {
        return stringSetToTextArea(getFixtureClasspaths());
    }

    public String doEditFixture() {
        try {
            SystemUnderTest selectedSut = SystemUnderTest.newInstance(selectedSutName);
            selectedSut.setProject(Project.newInstance(projectName));
            selectedSut = getPersistenceService().getSystemUnderTest(selectedSut, getHomeRepository());
            selectedSut.setFixtureClasspaths(ClasspathSet.parse(fixtureClasspath));
            getPersistenceService().updateSystemUnderTest(selectedSutName, selectedSut, getHomeRepository());
        } catch (LivingDocServerException e) {
            addActionError(e);
        }

        return doGetSystemUnderTests();
    }

    public String doSetAsDefault() {
        try {
            SystemUnderTest sut = SystemUnderTest.newInstance(selectedSutName);
            sut.setProject(Project.newInstance(projectName));
            getPersistenceService().setSystemUnderTestAsDefault(sut, getHomeRepository());
        } catch (LivingDocServerException e) {
            addActionError(e);
        }

        return doGetSystemUnderTests();
    }

    public String getVersion() {
        return getLivingDocConfluenceManager().getVersion();
    }

    public String doMigrateRegistration() {
        pagesToMigrate = getLivingDocConfluenceManager().getPageManager().getPages(getSpace(), false).stream().sequential()
                .filter(page ->
                        !page.getBodyAsString().contains(LivingDocPage.MACRO_KEY)
                        && getLivingDocConfluenceManager().isExecutable(page)
                ).collect(Collectors.toList());

        return SUCCESS;
    }

    public String doMigrateRegistrationLaunchProcess() {
        migratedPages = new ArrayList<>();

        getLivingDocConfluenceManager().getPageManager().getPages(getSpace(), false).stream().sequential()
            .filter(page ->
                    !page.getBodyAsString().contains(LivingDocPage.MACRO_KEY)
                    && getLivingDocConfluenceManager().isExecutable(page))
            .forEach(page -> {
                StringBuilder newPageBody = new StringBuilder(LivingDocPage.MACRO_HTML_CONTENT);
                page.setBodyAsString(newPageBody.append(page.getBodyAsString()).toString());
                migratedPages.add(page);
        });
        return SUCCESS;
    }

    public List<Page> getPagesToMigrate() {
        return pagesToMigrate;
    }

    public List<Page> getMigratedPages() {
        return migratedPages;
    }

    @Override
    public LinkedList<Project> getProjects() {
        if (projects != null)
            return projects;
        try {
            projects = new LinkedList<Project>(getPersistenceService().getAllProjects());
            projects.addLast(Project.newInstance(projectCreateOption()));
            projectName = projectName == null ? projects.iterator().next().getName() : projectName;

            return projects;
        } catch (LivingDocServerException e) {
            addActionError(e);
        }

        return projects;
    }

    public SystemUnderTest getSelectedSut() {
        return selectedSut;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName.trim();
    }

    @Override
    public String getProjectName() {
        return projectName;
    }

    @Override
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getPwd() {
        return pwd;
    }

    public void setPwd(String pwd) {
        this.pwd = StringUtils.trimToNull(pwd);
    }

    public String getEscapedPassword() {
        return StringEscapeUtils.escapeHtml(getPwd());
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = StringUtils.trimToNull(username);
    }

    public String getNewProjectName() {
        return newProjectName;
    }

    public void setNewProjectName(String newProjectName) {
        this.newProjectName = newProjectName.trim();
    }

    public String getNewSutName() {
        return newSutName;
    }

    public void setNewSutName(String newSutName) {
        this.newSutName = newSutName.trim();
    }

    public String getNewFixtureFactory() {
        return newFixtureFactory;
    }

    public void setNewFixtureFactory(String fixtureFactory) {
        this.newFixtureFactory = StringUtils.trimToNull(fixtureFactory);
    }

    public String getNewFixtureFactoryArgs() {
        return newFixtureFactoryArgs;
    }

    public void setNewFixtureFactoryArgs(String fixtureFactoryArgs) {
        this.newFixtureFactoryArgs = StringUtils.trimToNull(fixtureFactoryArgs);
    }

    public String getNewRunnerName() {
        return newRunnerName;
    }

    public void setNewRunnerName(String runnerName) {
        this.newRunnerName = runnerName.trim();
    }

    public String getSutClasspath() {
        return sutClasspath;
    }

    public void setSutClasspath(String sutClasspath) {
        this.sutClasspath = sutClasspath.trim();
    }

    public String getFixtureClasspath() {
        return fixtureClasspath;
    }

    public void setFixtureClasspath(String fixtureClasspath) {
        this.fixtureClasspath = fixtureClasspath.trim();
    }

    public String getNewProjectDependencyDescriptor() {
        return newProjectDependencyDescriptor;
    }

    public void setNewProjectDependencyDescriptor(String newProjectDependencyDescriptor) {
        this.newProjectDependencyDescriptor = StringUtils.trimToNull(newProjectDependencyDescriptor);
    }

    public Boolean getDefault() {
        return selected;
    }

    public void setDefault(Boolean selected) {
        this.selected = selected;
    }

    public List<Runner> getRunners() {
        return runners;
    }

    public String getSelectedSutName() {
        return selectedSutName;
    }

    public void setSelectedSutName(String selectedSutName) {
        this.selectedSutName = selectedSutName;
    }

    public boolean getReadonly() {
        return readonly;
    }

    public void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }

    public boolean isEditMode() {
        return editMode;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
    }

    public boolean isAddMode() {
        return addMode;
    }

    public void setAddMode(boolean addMode) {
        this.addMode = addMode;
    }

    public boolean isEditClasspathsMode() {
        return editClasspathsMode;
    }

    public void setEditClasspathsMode(boolean editClasspathsMode) {
        this.editClasspathsMode = editClasspathsMode;
    }

    public boolean isEditFixturesMode() {
        return editFixturesMode;
    }

    public void setEditFixturesMode(boolean editFixturesMode) {
        this.editFixturesMode = editFixturesMode;
    }

    public boolean isEditPropertiesMode() {
        return editPropertiesMode;
    }

    public void setEditPropertiesMode(boolean editPropertiesMode) {
        this.editPropertiesMode = editPropertiesMode;
    }

    @Override
    public List<SystemUnderTest> getSystemUnderTests() {
        return systemUnderTests;
    }

    private Project getProjectForRegistration() throws LivingDocServerException {
        if (isWithNewProject()) {
            validateProjectName();
            return Project.newInstance(newProjectName);
        }

        return Project.newInstance(projectName);
    }

    private void validateProjectName() throws LivingDocServerException {
        if (StringUtils.isBlank(newProjectName) || newProjectName.equals(getText("livingdoc.project.newname"))) {
            throw new LivingDocServerException("livingdoc.registration.invalidprojectname", "invalid name");
        }
    }

    private void successfullAction() {
        addMode = false;
        editPropertiesMode = false;
        editClasspathsMode = false;
        editFixturesMode = false;
        selectedSutName = newSutName;
        newSutName = "";
        newRunnerName = "";
        newFixtureFactory = null;
        newFixtureFactoryArgs = null;
        newProjectDependencyDescriptor = null;
    }

    private String getBaseUrl() {
        if (baseUrl != null)
            return baseUrl;
        baseUrl = getLivingDocConfluenceManager().getBaseUrl();
        return baseUrl;
    }

    private String repositoryBaseUrl() {
        StringBuilder sb = new StringBuilder();
        sb.append(getBaseUrl()).append("/display/").append(getSpaceKey());
        return sb.toString();
    }

    private String newTestUrl() {
        StringBuilder sb = new StringBuilder();
        sb.append(getBaseUrl());
        sb.append("?#").append(getSpaceKey());
        return sb.toString();
    }

    private void checkRepositoryBaseUrl() throws LivingDocServerException {

        if (!editMode && !addMode && !getRegisteredRepository().getBaseUrl().equals(getBaseUrl())) {
            addActionError(getText(LivingDocConfluenceManager.REPOSITORY_BASEURL_OUTOFSYNC, new String[]{getRegisteredRepository()
                    .getBaseUrl(), getBaseUrl()}));
        }
    }
}
