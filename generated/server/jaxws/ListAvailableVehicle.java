
package server.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "listAvailableVehicle", namespace = "http://server/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "listAvailableVehicle", namespace = "http://server/")
public class ListAvailableVehicle {

    @XmlElement(name = "managerID", namespace = "")
    private String managerID;

    /**
     * 
     * @return
     *     returns String
     */
    public String getManagerID() {
        return this.managerID;
    }

    /**
     * 
     * @param managerID
     *     the value for the managerID property
     */
    public void setManagerID(String managerID) {
        this.managerID = managerID;
    }

}
