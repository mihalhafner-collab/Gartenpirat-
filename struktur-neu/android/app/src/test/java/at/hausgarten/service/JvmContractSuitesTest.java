package at.hausgarten.service;

import org.junit.Test;

/** Runs each JVM contract suite (synthetic data only) as one JUnit test. */
public final class JvmContractSuitesTest {
    @Test
    public void accounting() throws Exception {
        AccountingTests.main(new String[0]);
    }

    @Test
    public void businessTableStore() throws Exception {
        BusinessTableStoreTests.main(new String[0]);
    }

    @Test
    public void equipmentMaintenance() throws Exception {
        EquipmentMaintenanceTests.main(new String[0]);
    }

    @Test
    public void equipmentProfilePhoto() throws Exception {
        EquipmentProfilePhotoTests.main(new String[0]);
    }

    @Test
    public void kotlinDomainRegression() throws Exception {
        KotlinDomainRegressionTests.main(new String[0]);
    }

    @Test
    public void kotlinFinanceRegression() throws Exception {
        KotlinFinanceRegressionTests.main(new String[0]);
    }

    @Test
    public void kotlinMcpRegression() throws Exception {
        KotlinMcpRegressionTests.main(new String[0]);
    }

    @Test
    public void pixelPlainBackup() throws Exception {
        PixelPlainBackupTests.main(new String[0]);
    }

    @Test
    public void settlementAccounting() throws Exception {
        SettlementAccountingTests.main(new String[0]);
    }

    @Test
    public void settlementProtection() throws Exception {
        SettlementProtectionTests.main(new String[0]);
    }

    @Test
    public void settlement() throws Exception {
        SettlementTests.main(new String[0]);
    }

    @Test
    public void workEntryAccessory() throws Exception {
        WorkEntryAccessoryTests.main(new String[0]);
    }
}
