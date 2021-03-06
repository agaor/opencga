package org.opencb.opencga.catalog.models;

/**
 * Created on 21/08/15
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class StudyPermissions {

    /**
     * Define the default permission for read resources
     */
    private boolean read;
    /**
     * Define the default permission for write or update resources
     */
    private boolean write;
    /**
     * Define the default permission for delete resources
     */
    private boolean delete;
    /**
     * Define if the group members are authorized to launch or execute jobs.
     * Also, will need READ permission for the input jobs, and WRITE permission for the output directory.
     */
    private boolean launchJobs;
    /**
     * Define if the group members are authorized to delete jobs from the db.
     */
    private boolean deleteJobs;
    /**
     * Set the group as Sample Manager
     * Define a set of permissions:
     *      Create, read, update and delete operations over all Samples
     *      Create, read, update and delete operations over all Individuals
     *      Create, read, update and delete operations over all Cohorts
     *      Create, read, update and delete operations over all VariableSets
     *
     */
    private boolean managerSamples;
    /**
     * Set the group as Study Manager
     * Define a set of permissions:
     *      Edit Study metadata information
     *      Create new Groups
     *      Add or remove users to a group
     *      Change group permissions
     *      Change resource ACLs
     */
    private boolean studyManager;

    public StudyPermissions() {
    }

    public StudyPermissions(boolean read, boolean write, boolean delete, boolean launchJobs, boolean deleteJobs, boolean managerSamples, boolean studyManager) {
        this.read = read;
        this.write = write;
        this.delete = delete;
        this.launchJobs = launchJobs;
        this.deleteJobs = deleteJobs;
        this.managerSamples = managerSamples;
        this.studyManager = studyManager;
    }

    @Override
    public String toString() {
        return "StudyPermissions{" +
                "read=" + read +
                ", write=" + write +
                ", delete=" + delete +
                ", launchJobs=" + launchJobs +
                ", deleteJobs=" + deleteJobs +
                ", managerSamples=" + managerSamples +
                ", studyManager=" + studyManager +
                '}';
    }

    public boolean isRead() {
        return read;
    }

    public StudyPermissions setRead(boolean read) {
        this.read = read;
        return this;
    }

    public boolean isWrite() {
        return write;
    }

    public StudyPermissions setWrite(boolean write) {
        this.write = write;
        return this;
    }

    public boolean isDelete() {
        return delete;
    }

    public StudyPermissions setDelete(boolean delete) {
        this.delete = delete;
        return this;
    }

    public boolean isLaunchJobs() {
        return launchJobs;
    }

    public StudyPermissions setLaunchJobs(boolean launchJobs) {
        this.launchJobs = launchJobs;
        return this;
    }

    public boolean isDeleteJobs() {
        return deleteJobs;
    }

    public StudyPermissions setDeleteJobs(boolean deleteJobs) {
        this.deleteJobs = deleteJobs;
        return this;
    }

    public boolean isManagerSamples() {
        return managerSamples;
    }

    public StudyPermissions setManagerSamples(boolean managerSamples) {
        this.managerSamples = managerSamples;
        return this;
    }

    public boolean isStudyManager() {
        return studyManager;
    }

    public StudyPermissions setStudyManager(boolean studyManager) {
        this.studyManager = studyManager;
        return this;
    }
}
