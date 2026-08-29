package com.zerointrusion.data.repository.connecteddevices

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectedDevicesResponseParserTest {
    private val parser = ConnectedDevicesResponseParser(Gson())

    @Test
    fun `parse keeps namespace entries when backend omits devices array`() {
        val response = """
            {
              "success": true,
              "accounts": [
                {
                  "accountId": "account-1",
                  "namespaces": [
                    {
                      "accountId": "account-1",
                      "deviceId": "desktop-1",
                      "namespaceId": "namespace-1",
                      "name": "Primary",
                      "url": "https://server-a.test"
                    },
                    {
                      "accountId": "account-1",
                      "deviceId": "desktop-1",
                      "namespaceId": "namespace-2",
                      "name": "Backup",
                      "url": "https://server-b.test"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = parser.parse(response)

        assertEquals(1, parsed.groups.size)
        assertEquals(null, parsed.groups.single().deviceName)
        assertEquals(2, parsed.groups.single().namespaces.size)
        assertEquals(2, parsed.namespaces.size)
        assertEquals(listOf("Backup", "Primary"), parsed.namespaces.map { it.namespaceName })
        assertEquals(listOf("desktop-1", "desktop-1"), parsed.namespaces.map { it.deviceId })
    }
}