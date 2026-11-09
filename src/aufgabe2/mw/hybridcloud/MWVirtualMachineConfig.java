package mw.hybridcloud;

public class MWVirtualMachineConfig {

  public String vmName;

  public String imageName;
  public String imageId;

  // OpenStack: boot from a new volume created from this volume snapshot instead of an image
  public String snapshotName;
  public String snapshotId;

  public String flavorName;
  public String flavorId;

  public String networkName;
  public String networkId;

  public String securityGroup;
  public String keyName;

  public String userData;

  public MWVirtualMachineConfig(
      String vmName,
      String imageName,
      String imageId,
      String snapshotName,
      String snapshotId,
      String flavorName,
      String flavorId,
      String networkName,
      String networkId,
      String securityGroup,
      String keyName,
      String userData) {
    this.vmName = vmName;
    this.imageName = imageName;
    this.imageId = imageId;
    this.snapshotName = snapshotName;
    this.snapshotId = snapshotId;
    this.flavorName = flavorName;
    this.flavorId = flavorId;
    this.networkName = networkName;
    this.networkId = networkId;
    this.securityGroup = securityGroup;
    this.keyName = keyName;
    this.userData = userData;
  }

  @Override
  public String toString() {
    return "MWVirtualMachineConfig [vmName="
        + vmName
        + ", image="
        + imageName
        + "@"
        + imageId
        + ", snapshot="
        + snapshotName
        + "@"
        + snapshotId
        + ", flavor="
        + flavorName
        + "@"
        + flavorId
        + ", network="
        + networkName
        + "@"
        + networkId
        + ", securityGroup="
        + securityGroup
        + ", keyName="
        + keyName
        + ", userData="
        + userData
        + "]";
  }
}
