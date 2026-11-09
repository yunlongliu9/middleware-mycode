package mw.hybridcloud;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceState;
import software.amazon.awssdk.services.ec2.model.InstanceStateChange;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.model.RunInstancesMonitoringEnabled;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.TagSpecification;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesResponse;

public class MWCloudPlatformAWS implements MWCloudPlatform {

  private static final String VM_NAME_KEY = "Name";

  private static final class InstanceStateCodes {

    // start: PENDING --> RUNNING
    public static final int PENDING = 0;
    public static final int RUNNING = 16;

    // terminate: RUNNING/PENDING/STOPPING/STOPPED --> SHUTTING_DOWN --> TERMINATED
    public static final int SHUTTING_DOWN = 32;
    public static final int TERMINATED = 48;

    // stop: RUNNING --> STOPPING --> STOPPED
    public static final int STOPPING = 64;
    public static final int STOPPED = 80;

    private InstanceStateCodes() {}
  }

  private final Ec2Client ec2;

  public MWCloudPlatformAWS() {
    this.ec2 = Ec2Client.builder().region(Region.EU_WEST_1).build();
  }

  private void log(String format, Object... args) {
    System.err.printf("[AWS] " + format + "\n", args);
  }

  private String extractNameFromTags(Instance instance) {
    if (!instance.hasTags()) {
      this.log("failed to extract name: instance %s has no tags", instance.instanceId());
      return null;
    }
    for (Tag tag : instance.tags()) {
      if (VM_NAME_KEY.equals(tag.key())) {
        return tag.value();
      }
    }
    this.log(
        "failed to extract name: instance %s has no \"%s\" tag",
        instance.instanceId(), VM_NAME_KEY);
    return null;
  }

  private MWVirtualMachine instanceToVM(Instance instance) {
    String name = this.extractNameFromTags(instance);
    if (name == null) {
      name = "<unknown>";
    }

    MWVirtualMachine vm = new MWVirtualMachine(instance.instanceId(), name, instance.ipv6Address());
    vm.lastState = instance.state().name().toString();
    return vm;
  }

  private String stateToString(InstanceState state) {
    StringBuilder sb = new StringBuilder();
    sb.append(state.name());
    sb.append("|");
    sb.append(state.code());
    return sb.toString();
  }

  private String instanceToString(Instance instance) {
    StringBuilder sb = new StringBuilder();
    sb.append("Instance [");
    sb.append("id=");
    sb.append(instance.instanceId());
    sb.append(", ");
    sb.append("name=");
    sb.append(this.extractNameFromTags(instance));
    sb.append(", ");
    sb.append("state=");
    sb.append(this.stateToString(instance.state()));
    sb.append("]");
    return sb.toString();
  }

  private Instance runInstance(MWVirtualMachineConfig conf) throws MWCloudException {
    this.log("runInstance(%s)", conf);

    Tag nameTag = Tag.builder().key(VM_NAME_KEY).value(conf.vmName).build();
    TagSpecification spec =
        TagSpecification.builder().tags(nameTag).resourceType("instance").build();

    RunInstancesRequest request =
        RunInstancesRequest.builder()
            .imageId(conf.imageId) // Amazon Linux Image from the exercise sheet
            .tagSpecifications(spec)
            .instanceType("t2.nano")
            .minCount(1)
            .maxCount(1)
            .keyName(conf.keyName)
            .userData(Base64.getEncoder().encodeToString(conf.userData.getBytes()))
            .monitoring(RunInstancesMonitoringEnabled.builder().enabled(true).build())
            .securityGroupIds(conf.securityGroup)
            .subnetId(conf.networkId)
            .build();
    RunInstancesResponse response = ec2.runInstances(request);
    if (!response.hasInstances() || response.instances().isEmpty()) {
      throw new MWCloudException(
          "" + response.getClass().getName() + " does not contain any instances");
    }
    if (response.instances().size() >= 2) {
      throw new MWCloudException(
          "" + response.getClass().getName() + " contains too many instances");
    }

    Instance instance = response.instances().get(0);
    this.log("runInstance ==> %s", this.instanceToString(instance));
    return instance;
  }

  private List<Instance> listAllInstances() throws MWCloudException {
    this.log("listAllInstances()");

    DescribeInstancesRequest request = DescribeInstancesRequest.builder().build();
    DescribeInstancesResponse response = ec2.describeInstances(request);
    if (!response.hasReservations()) {
      throw new MWCloudException(
          "" + response.getClass().getName() + " does not contain any reservations");
    }

    List<Instance> instances =
        response.reservations().stream()
            .filter(Reservation::hasInstances)
            .flatMap((r) -> r.instances().stream())
            .collect(Collectors.toList());
    this.log(
        "listAllInstances ==> [%s]",
        instances.stream().map(Instance::instanceId).collect(Collectors.joining(", ")));
    return instances;
  }

  private Instance describeInstance(String instanceId) throws MWCloudException {
    this.log("describeInstance(%s)", instanceId);

    DescribeInstancesRequest request =
        DescribeInstancesRequest.builder().instanceIds(instanceId).build();
    DescribeInstancesResponse response = ec2.describeInstances(request);
    if (!response.hasReservations() || response.reservations().isEmpty()) {
      throw new MWCloudException(
          "" + response.getClass().getName() + " does not contain any reservations");
    }
    if (response.reservations().size() >= 2) {
      throw new MWCloudException(
          "" + response.getClass().getName() + " contains too many reservations");
    }

    Reservation reservation = response.reservations().get(0);
    if (!reservation.hasInstances() || reservation.instances().isEmpty()) {
      throw new MWCloudException(
          "" + reservation.getClass().getName() + " does not contain any instances");
    }
    if (reservation.instances().size() >= 2) {
      throw new MWCloudException(
          "" + reservation.getClass().getName() + " contains too many instances");
    }

    Instance instance = reservation.instances().get(0);
    this.log("describeInstance ==> %s", this.instanceToString(instance));
    return instance;
  }

  private InstanceStateChange terminateInstance(String instanceId) throws MWCloudException {
    this.log("terminateInstance(%s)", instanceId);

    TerminateInstancesRequest request =
        TerminateInstancesRequest.builder().instanceIds(instanceId).build();
    TerminateInstancesResponse response = ec2.terminateInstances(request);
    if (!response.hasTerminatingInstances() || response.terminatingInstances().isEmpty()) {
      throw new MWCloudException(
          "" + response.getClass().getName() + " does not contain any terminatingInstances");
    }
    if (response.terminatingInstances().size() >= 2) {
      throw new MWCloudException(
          "" + response.getClass().getName() + " contains too many terminatingInstances");
    }

    InstanceStateChange instanceStateChange = response.terminatingInstances().get(0);
    this.log("terminateInstance ==> %s", instanceStateChange);
    return instanceStateChange;
  }

  private void waitFor(Duration duration) throws MWCloudException {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException ie) {
      throw new MWCloudException("interrupted while waiting for instance", ie);
    }
  }

  @Override
  public MWVirtualMachine startVM(MWVirtualMachineConfig conf) throws MWCloudException {
    Instance instance = this.runInstance(conf);
    this.log("state = %s", this.stateToString(instance.state()));
    while (instance.state().code() == InstanceStateCodes.PENDING) {
      this.waitFor(Duration.ofSeconds(10));
      instance = this.describeInstance(instance.instanceId());
      this.log("state = %s", this.stateToString(instance.state()));
    }
    if (instance.state().code() != InstanceStateCodes.RUNNING) {
      throw new MWCloudException("failed to start VM, state " + instance.state().name());
    }

    return this.instanceToVM(instance);
  }

  @Override
  public void deleteVM(MWVirtualMachine vmRef) throws MWCloudException {
    InstanceStateChange instanceStateChange = this.terminateInstance(vmRef.vmId);
    this.log("previous state = %s", this.stateToString(instanceStateChange.previousState()));
    this.log("current state = %s", this.stateToString(instanceStateChange.currentState()));
    InstanceState instanceState = instanceStateChange.currentState();
    while (instanceState.code() == InstanceStateCodes.RUNNING
        || instanceState.code() == InstanceStateCodes.SHUTTING_DOWN) {
      this.waitFor(Duration.ofSeconds(10));
      instanceState = this.describeInstance(vmRef.vmId).state();
      this.log("updated state = %s", this.stateToString(instanceStateChange.currentState()));
    }
    if (instanceState.code() != InstanceStateCodes.TERMINATED) {
      throw new MWCloudException("failed to delete VM, state " + instanceState.name());
    }
  }

  @Override
  public List<MWVirtualMachine> listVMs() throws MWCloudException {
    return this.listAllInstances().stream().map(this::instanceToVM).collect(Collectors.toList());
  }

  @Override
  public Double getCPUUsage(MWVirtualMachine vm, int seconds) throws MWCloudException {
    /*
     *  TODO: Implement method (optional for 5.0 ECTS)
     */
    return null;
  }

  public static void main(String[] args) {
    final String AMAZON_LINUX_IMAGE_ID =
        "ami-0f2c5fdf507752903"; // Amazon Linux from the exercise sheet
    final String SUBNET_ID =
        "subnet-9f4e11f8"; // https://eu-west-1.console.aws.amazon.com/vpcconsole/home?region=eu-west-1#SubnetDetails:subnetId=subnet-9f4e11f8
    final String SECURITY_GROUP_ID =
        "sg-800bd0fb"; // https://eu-west-1.console.aws.amazon.com/ec2/home?region=eu-west-1#SecurityGroup:groupId=sg-800bd0fb
    final String KEY_NAME = "gruppe06"; // ~/.aws/gruppe06.pem

    MWVirtualMachineConfig conf =
        new MWVirtualMachineConfig(
            "VM_NAME",
            "IMAGE_NAME",
            AMAZON_LINUX_IMAGE_ID,
            "SNAPSHOT_NAME",
            "SNAPSHOT_ID",
            "FLAVOR_NAME",
            "FLAVOR_ID",
            "NETWORK_NAME",
            SUBNET_ID,
            SECURITY_GROUP_ID,
            KEY_NAME,
            "USER_DATA");

    MWCloudPlatformAWS platform = new MWCloudPlatformAWS();

    try {
      MWVirtualMachine vm = platform.startVM(conf);
      System.out.println("startVM: " + vm);

      for (MWVirtualMachine vm_ : platform.listVMs()) {
        System.out.println("listVMs: " + vm_);
      }

      platform.deleteVM(vm);
      System.out.println("deleteVM: ok");

      for (MWVirtualMachine vm_ : platform.listVMs()) {
        System.out.println("listVMs: " + vm_);
      }
    } catch (MWCloudException ce) {
      System.out.println("error: " + ce);
    }
  }
}
