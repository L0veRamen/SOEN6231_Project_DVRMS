
package server.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "removeVehicle", namespace = "http://server/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "removeVehicle", namespace = "http://server/", propOrder = {
    "managerID",
    "vehicleID"
})
public class RemoveVehicle {

    @XmlElement(name = "managerID", namespace = "")
    private String managerID;
    @XmlElement(name = "vehicleID", namespace = "")
    private String vehicleID;

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

    /**
     * 
     * @return
     *     returns String
     */
    public String getVehicleID() {
        return this.vehicleID;
    }

    /**
     * 
     * @param vehicleID
     *     the value for the vehicleID property
     */
    public void setVehicleID(String vehicleID) {
        this.vehicleID = vehicleID;
    }

}
