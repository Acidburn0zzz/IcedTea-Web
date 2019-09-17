// Copyright (C) 2001-2003 Jon A. Maxwell (JAM)
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

package net.sourceforge.jnlp.cache;

import net.adoptopenjdk.icedteaweb.jnlp.version.VersionString;
import net.sourceforge.jnlp.DownloadOptions;
import net.sourceforge.jnlp.runtime.JNLPRuntime;
import net.sourceforge.jnlp.util.UrlUtils;
import net.sourceforge.jnlp.util.WeakList;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * Information about a single resource to download.
 * This class tracks the downloading of various resources of a
 * JNLP file to local files.  It can be used to download icons,
 * jnlp and extension files, jars, and jardiff files using the
 * version based protocol or any file using the basic download
 * protocol.
 * </p>
 * <p>
 * Resources can be put into download groups by specifying a part
 * name for the resource.  The resource tracker can also be
 * configured to prefetch resources, which are downloaded in the
 * order added to the media tracker.
 * </p>
 *
 * @author <a href="mailto:jmaxwell@users.sourceforge.net">Jon A. Maxwell (JAM)</a> - initial author
 * @version $Revision: 1.9 $
 */
public class Resource {
    // todo: fix resources to handle different versions

    // todo: IIRC, any resource is checked for being up-to-date
    // only once, regardless of UpdatePolicy.  verify and fix.

    public enum Status {
        PRECONNECT,
        CONNECTING,
        CONNECTED,
        PREDOWNLOAD,
        DOWNLOADING,
        DOWNLOADED,
        ERROR,
        PROCESSING // in queue or being worked on
    }

    /** list of weak references of resources currently in use */
    private static final WeakList<Resource> resources = new WeakList<>();

    /** weak list of trackers monitoring this resource */
    private final WeakList<ResourceTracker> trackers = new WeakList<>();

    /** the remote location of the resource */
    private final URL location;

    /** the location to use when downloading */
    private URL downloadLocation;

    /** the local file downloaded to */
    private File localFile;

    /** the requested version */
    private final VersionString requestVersion;

    /** the version downloaded from server */
    private VersionString downloadVersion;

    /** amount in bytes transferred */
    private volatile long transferred = 0;

    /** total size of the resource, or -1 if unknown */
    private volatile long size = -1;

    /** the status of the resource */
    private final EnumSet<Status> status = EnumSet.noneOf(Status.class);

    /** Update policy for this resource */
    @Deprecated
    private final UpdatePolicy updatePolicy;

    private UpdateOptions updateOptions;

    /** Download options for this resource */
    private final DownloadOptions downloadOptions;


    /**
     * Create a resource.
     */
    private Resource(final URL location, final VersionString requestVersion, final DownloadOptions downloadOptions, final UpdatePolicy updatePolicy, final UpdateOptions updateOptions) {
        this.location = location;
        this.downloadLocation = location;
        this.requestVersion = requestVersion;
        this.downloadOptions = downloadOptions;
        this.updatePolicy = updatePolicy;
        this.updateOptions = updateOptions;
    }

    /**
     * Creates and returns a shared Resource object representing the given
     * location and version.
     *
     * @param location       final location of resource
     * @param requestVersion final version of resource
     * @param updatePolicy   final policy for updating
     * @return new resource, which is already added in resources list
     */
    static Resource createResource(final URL location, final VersionString requestVersion, final DownloadOptions downloadOptions, final UpdatePolicy updatePolicy, final UpdateOptions updateOptions) {
        synchronized (resources) {
            Resource resource = new Resource(location, requestVersion, downloadOptions, updatePolicy, updateOptions);

            //FIXME - url ignores port during its comparison
            //this may affect test-suites
            int index = resources.indexOf(resource);
            if (index >= 0) { // return existing object
                Resource result = resources.get(index);
                if (result != null) {
                    return result;
                }
            }

            resources.add(resource);
            resources.trimToSize();

            return resource;
        }
    }

    /**
     * Returns the remote location of the resource.
     *
     * @return the same location as the one with which this resource was created
     */
    public URL getLocation() {
        return location;
    }

    /**
     * Returns the URL to use for downloading the resource. This can be
     * different from the original location since it may use a different
     * file name to support versioning and compression
     *
     * @return the url to use when downloading
     */
    URL getDownloadLocation() {
        return downloadLocation;
    }

    /**
     * Set the url to use for downloading the resource
     *
     * @param downloadLocation url to be downloaded
     */
    void setDownloadLocation(URL downloadLocation) {
        this.downloadLocation = downloadLocation;
    }

    /**
     * @return the local file currently being downloaded
     */
    File getLocalFile() {
        return localFile;
    }

    /**
     * Sets the local file to be downloaded
     *
     * @param localFile location of stored resource
     */
    void setLocalFile(File localFile) {
        this.localFile = localFile;
    }

    /**
     * @return the requested version
     */
    VersionString getRequestVersion() {
        return requestVersion;
    }

    /**
     * @return the version downloaded from server
     */
    VersionString getDownloadVersion() {
        return downloadVersion;
    }

    /**
     * Sets the version downloaded from server
     *
     * @param downloadVersion version of downloaded resource
     */
    public void setDownloadVersion(final VersionString downloadVersion) {
        this.downloadVersion = downloadVersion;
    }

    /**
     * @return the amount in bytes transferred
     */
    long getTransferred() {
        return transferred;
    }

    /**
     * Sets the amount transferred
     *
     * @param transferred set the whole transferred amount to this value
     */
    void setTransferred(long transferred) {
        this.transferred = transferred;
    }

    /**
     * Returns the size of the resource
     *
     * @return size of resource (-1 if unknown)
     */
    public long getSize() {
        return size;
    }

    /**
     * Sets the size of the resource
     *
     * @param size desired size of resource
     */
    public void setSize(long size) {
        this.size = size;
    }

    /**
     * @return the status of the resource
     */
    Set<Status> getCopyOfStatus() {
        return EnumSet.copyOf(status);

    }

    /**
     * Check if the specified flag is set.
     *
     * @param flag a status flag
     * @return true iff the flag is set
     */
    boolean isSet(Status flag) {
        synchronized (status) {
            return status.contains(flag);
        }
    }

    /**
     * Check if all the specified flags are set.
     *
     * @param flags a collection of flags
     * @return true iff all the flags are set
     */
    boolean hasAllFlags(Collection<Status> flags) {
        synchronized (status) {
            return status.containsAll(flags);
        }
    }

    boolean isComplete() {
        synchronized (status) {
            return isSet(Status.ERROR) || isSet(Status.DOWNLOADED);
        }
    }

    /**
     * @return the update policy for this resource
     */
    UpdatePolicy getUpdatePolicy() {
        return this.updatePolicy;
    }

    /**
     * Returns a human-readable status string.
     */
    private String getStatusString() {
        StringBuilder result = new StringBuilder();

        synchronized (status) {
            if (status.isEmpty()) {
                return "<>";
            }
            for (Status stat : status) {
                result.append(stat.toString()).append(" ");
            }
        }

        return result.toString().trim();
    }

    /**
     * Changes the status by clearing the flags in the first
     * parameter and setting the flags in the second.  This method
     * is synchronized on this resource.
     *
     * @param clear a collection of status flags to unset
     * @param add   a collection of status flags to set
     */
    void changeStatus(Collection<Status> clear, Collection<Status> add) {
        synchronized (status) {
            if (clear != null) {
                status.removeAll(clear);
            }
            if (add != null) {
                status.addAll(add);
            }
        }
    }

    /**
     * Clear all flags
     */
    void resetStatus() {
        synchronized (status) {
            status.clear();
        }
    }

    /**
     * Removes the tracker to the list of trackers monitoring this
     * resource.
     *
     * @param tracker tracker to be removed
     */
    void removeTracker(ResourceTracker tracker) {
        synchronized (trackers) {
            trackers.remove(tracker);
            trackers.trimToSize();
        }
    }

    /**
     * Adds the tracker to the list of trackers monitoring this
     * resource.
     *
     * @param tracker to observing resource
     */
    void addTracker(ResourceTracker tracker) {
        synchronized (trackers) {
            // prevent GC between contains and add
            List<ResourceTracker> t = trackers.hardList();
            if (!t.contains(tracker))
                trackers.add(tracker);

            trackers.trimToSize();
        }
    }

    DownloadOptions getDownloadOptions() {
        return this.downloadOptions;
    }

    public UpdateOptions getUpdateOptions() {
        return updateOptions;
    }

    boolean isConnectable() {
        return JNLPRuntime.isConnectable(this.location);
    }

    @Override
    public int hashCode() {
        // FIXME: should probably have a better hashcode than this, but considering
        // #equals(Object) was already defined first (without also overriding hashcode!),
        // this is just being implemented in line with that so we don't break HashMaps,
        // HashSets, etc
        return location.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Resource) {
            // this prevents the URL handler from looking up the IP
            // address and doing name resolution; much faster so less
            // time spent in synchronized addResource determining if
            // Resource is already in a tracker, and better for offline
            // mode on some OS.
            return UrlUtils.urlEquals(location, ((Resource) other).location);
        }
        return false;
    }

    @Override
    public String toString() {
        return "location=" + location.toString() + " state=" + getStatusString();
    }
}
