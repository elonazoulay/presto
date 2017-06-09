package com.facebook.presto.execution.resourceGroups.db;

import com.facebook.presto.resourceGroups.db.H2ResourceGroupsDao;
import com.facebook.presto.testing.MaterializedResult;
import com.facebook.presto.testing.MaterializedRow;
import com.facebook.presto.tests.DistributedQueryRunner;
import org.testng.annotations.Test;

import static com.facebook.presto.execution.resourceGroups.db.H2TestUtil.createQueryRunner;
import static com.facebook.presto.execution.resourceGroups.db.H2TestUtil.getDao;
import static com.facebook.presto.execution.resourceGroups.db.H2TestUtil.getDbConfigUrl;
import static com.facebook.presto.execution.resourceGroups.db.H2TestUtil.adminSession;
import static org.testng.Assert.assertEquals;

public class TestResourceGroupSelectorSystemTable
{
    private static final String SELECTOR_SPECS_QUERY = "SELECT resource_group_id,\n" +
            "  user_regex,\n" +
            "  source_regex\n" +
            "FROM resource_group_managers.system.selectors";

    @Test
    public void testResourceGroupSelectorSystemTable()
            throws Exception
    {
        String dbConfigUrl = getDbConfigUrl();
        H2ResourceGroupsDao dao = getDao(dbConfigUrl);

        try (DistributedQueryRunner queryRunner = createQueryRunner(dbConfigUrl, dao)) {
            MaterializedResult result = getSelectorsQuery(queryRunner);
            assertEquals(result.getRowCount(), 4);
            for (MaterializedRow row : result.getMaterializedRows()) {
                if (row.getField(0).toString().equals("global.user-${USER}.dashboard-${USER}")) {
                    assertEquals(row.getField(1), "user.*");
                    break;
                }
            }
            dao.updateSelector(5L, "foo", "(?i).*dashboard.*", "user.*", "(?i).*dashboard.*");
            Thread.sleep(2000);
            result = getSelectorsQuery(queryRunner);
            for (MaterializedRow row : result.getMaterializedRows()) {
                if (row.getField(0).toString().equals("global.user-${USER}.dashboard-${USER}")) {
                    assertEquals(row.getField(1), "foo");
                    break;
                }
            }
            dao.deleteSelector(5L, "foo", "(?i).*dashboard.*");
            Thread.sleep(2000);
            result = getSelectorsQuery(queryRunner);
            assertEquals(result.getRowCount(), 3);
        }
    }

    private static MaterializedResult getSelectorsQuery(DistributedQueryRunner queryRunner)
    {
        return queryRunner.execute(adminSession(), SELECTOR_SPECS_QUERY);
    }

}
